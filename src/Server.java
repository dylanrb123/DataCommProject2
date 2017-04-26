import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Server thread
 */
public class Server {
    private DatagramSocket socket;
    private int maxSegmentSize;
    private boolean isVerbose;
    private MessageDigest md5Digest;
    private TcpConnectionState connectionState;
    private int sequenceNumber;
    private int ackNumber;
    private InetAddress clientAddress = null;
    private int clientPort = -1;
    private int clientSequenceNumber = -1;
    private int lastAckNumber = -1;
    private HashMap<Integer, TcpPacket> packetCache;

    /**
     * Constructs the server thread, creates the UDP socket to communicate with the client
     * @param port port to listen on
     * @param maxSegmentSize max segment size to send over the connection
     * @param isVerbose turn on verbose mode
     * @throws IOException if there are UDP errors
     * @throws NoSuchAlgorithmException this shouldn't happen
     */
    public Server(int port, int maxSegmentSize, boolean isVerbose) throws IOException, NoSuchAlgorithmException {
        this.socket = new DatagramSocket(port);
        if (isVerbose) System.out.println("Listening on port " + port + "...");
        this.maxSegmentSize = maxSegmentSize;
        this.isVerbose = isVerbose;
        this.md5Digest = MessageDigest.getInstance("MD5");
        this.connectionState = TcpConnectionState.CLOSED;
        this.sequenceNumber = 0;
        this.ackNumber = 0;
        this.packetCache = new HashMap<>();
    }

    public void doTheThing() {
        while(true) {
            try {
                if (this.connectionState != TcpConnectionState.ESTABLISHED) {
                    listenForHandshake();
                }
                receiveFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Performs the server's part of the three way handshake
     * @throws IOException if there's weird stuff with the UDP port
     */
    private void listenForHandshake() throws IOException {
        this.connectionState = TcpConnectionState.LISTEN;
        if (this.isVerbose) System.out.println("Listening for handshake...");
        while (this.connectionState != TcpConnectionState.SYN_RECEIVED) {
            if (this.isVerbose) System.out.println("Waiting for SYN...");
            TcpPacket synPacket = receivePacketForHandshake();
            if (synPacket.getHeader().getIsRst() == 1) {
                if (this.isVerbose) System.out.println("Received RST from client, restarting handshake...");
                continue;
            }
            if (!synPacket.validateChecksum()) {
                if (this.isVerbose) System.out.println("Received corrupted packet from client waiting for SYN");
                continue;
            }
            if (synPacket.getHeader().getIsSyn() != 1) {
                if (this.isVerbose) System.out.println("Received packet from client with incorrect CTRL flags");
                continue;
            }
            if (this.isVerbose) {
                System.out.println("Received SYN from client");
                System.out.println("Sending SYN-ACK...");
            }
            this.connectionState = TcpConnectionState.SYN_RECEIVED;
            this.clientSequenceNumber = synPacket.getHeader().getSequenceNumber();
        }
        // add one to the sequence number even though no data was received, special case
        TcpPacket synAckPacket = createSynAckPacket(this.sequenceNumber, this.clientSequenceNumber++);
        this.lastAckNumber = this.clientSequenceNumber;
        sendPacket(synAckPacket);
        if (this.isVerbose) System.out.println("Waiting for ACK...");
        TcpPacket ackPacket = receivePacketForHandshake();
        if (ackPacket.getHeader().getIsRst() == 1) {
            if (this.isVerbose) System.out.println("Received RST from client, restarting handshake...");
            listenForHandshake();
            return;
        }
        if (!ackPacket.validateChecksum()) {
            if (this.isVerbose) {
                System.out.println("Received corrupted packet from client waiting for ACK");
                System.out.println("Resetting connection...");
            }

            TcpPacket rstPacket = createRstPacket();
            sendPacket(rstPacket);
            listenForHandshake();
            return;
        }
        if (ackPacket.getHeader().getIsAck() != 1) {
            if (this.isVerbose) {
                System.out.println("Received packet from client with incorrect CTRL flags");
                System.out.println("Resetting connection...");
            }
            TcpPacket rstPacket = createRstPacket();
            sendPacket(rstPacket);
            listenForHandshake();
            return;
        }
        if (this.isVerbose) {
            System.out.println("Received ACK from client");
            System.out.println("Connection established on server!");
        }
        this.connectionState = TcpConnectionState.ESTABLISHED;
    }

    /**
     * Receive the file from the client. Deals with out of order packets. Prints MD5 checksum of the file when the
     * client sends a FIN packet
     * @throws IOException weird UDP stuff
     */
    private void receiveFile() throws IOException {
        if (this.isVerbose) System.out.println("Waiting for packet from client...");
        while (true) {
            TcpPacket packetFromClient = receivePacket();
            if (!packetFromClient.validateChecksum()) {
                if (this.isVerbose) System.out.println("Received corrupted packet from client, sending duplicate ack");
                // send duplicate ack
                TcpPacket duplicateAck = createAckPacket(this.lastAckNumber);
                sendPacket(duplicateAck);
            }
            if (this.isVerbose) System.out.println("Received packet from client");
            if (this.isVerbose) System.out.println("Updating digest");
            md5Digest.update(packetFromClient.getData());
            this.clientSequenceNumber = packetFromClient.getHeader().getSequenceNumber();
            // if the sequence number equals the last one we ACKed, order is good. Send ack.
            if (clientSequenceNumber == this.lastAckNumber) {
                this.lastAckNumber = this.clientSequenceNumber + packetFromClient.getData().length;
                TcpPacket ackPacket = createAckPacket(lastAckNumber);
                if (this.isVerbose) System.out.println("Sending ACK with number " + this.lastAckNumber);
                sendPacket(ackPacket);
                checkCache(this.lastAckNumber);
            } else {
                this.packetCache.put(packetFromClient.getHeader().getSequenceNumber(), packetFromClient);
            }
            // calculate the final checksum
            if (packetFromClient.getHeader().getIsFin() == 1) {
                byte[] md5Bytes = md5Digest.digest();
                System.out.println("MD5: " + DatatypeConverter.printHexBinary(md5Bytes));
                this.connectionState = TcpConnectionState.CLOSED;
                return;
            }
        }

    }

    private void checkCache(int lastAckNumber) throws IOException {
        if (this.packetCache.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Integer, TcpPacket>> cacheIterator = this.packetCache.entrySet().iterator();
        while (cacheIterator.hasNext()) {
            Map.Entry<Integer, TcpPacket> cacheEntry = cacheIterator.next();
            if (cacheEntry.getKey() == lastAckNumber) {
                if (this.isVerbose) System.out.println("ACKing packet from cache with sequence number " + cacheEntry.getKey());
                TcpPacket ackPacket = createAckPacket(cacheEntry.getKey());
                this.lastAckNumber = cacheEntry.getKey();
                sendPacket(ackPacket);
                cacheIterator.remove();
                checkCache(this.lastAckNumber);
            }
        }
    }

    /**
     * Receives a single packet from the client
     * @return the TCP packet received from the client
     * @throws IOException bleh
     */
    private TcpPacket receivePacketForHandshake() throws IOException {
        byte[] buf = new byte[20];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        this.clientAddress = packet.getAddress();
        this.clientPort = packet.getPort();
        return TcpPacket.deserialize(packet.getData());
    }

    private TcpPacket receivePacket() throws IOException {
        // TODO: fix so it doesn't rely on padding
        byte[] buf = new byte[this.maxSegmentSize];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        this.clientAddress = packet.getAddress();
        this.clientPort = packet.getPort();
        return TcpPacket.deserialize(packet.getData());
    }

    /**
     * Sends a single packet to the client. Sets the checksum field before sending.
     * @param tcpPacket packet to send
     * @throws IOException bleh
     */
    private void sendPacket(TcpPacket tcpPacket) throws IOException {
        // SETS THE CHECKSUM FIELD IN THE HEADER
        tcpPacket.calculateChecksum();
        byte[] tcpPacketBytes = tcpPacket.serialize();
        DatagramPacket udpPacket = new DatagramPacket(tcpPacketBytes, tcpPacketBytes.length,
                this.clientAddress, this.clientPort);
        socket.send(udpPacket);
    }

    /**
     * Creates an empty RST packet
     * @return the packet
     */
    private TcpPacket createRstPacket() {
        TcpHeader rstHeader = new TcpHeader(0, 0, 0, 1, 0, 0, 0, 0);
        return new TcpPacket(rstHeader, new byte[0]);
    }

    /**
     * Creates an empty SYN-ACK packet
     * @param sequenceNumber sequence number to send
     * @param ackNumber ack number to send
     * @return the packet
     */
    private TcpPacket createSynAckPacket(int sequenceNumber, int ackNumber) {
        TcpHeader synAckHeader = new TcpHeader(sequenceNumber, ackNumber, 1, 0, 1, 0, 0, 0);
        return new TcpPacket(synAckHeader, new byte[]{});
    }

    private TcpPacket createAckPacket(int ackNumber) {
        return new TcpPacket(new TcpHeader(0, ackNumber, 1, 0, 0, 0, 0, 0), new byte[0]);
    }
}
