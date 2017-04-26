import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ThreadLocalRandom;

public class ServerThread extends Thread {
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

    public ServerThread(int port, int maxSegmentSize, boolean isVerbose) throws IOException, NoSuchAlgorithmException {
        super("ServerThread");
        this.socket = new DatagramSocket(port);
        if (isVerbose) System.out.println("Listening on port " + port + "...");
        this.maxSegmentSize = maxSegmentSize;
        this.isVerbose = isVerbose;
        this.md5Digest = MessageDigest.getInstance("MD5");
        this.connectionState = TcpConnectionState.CLOSED;
        this.sequenceNumber = 0;
        this.ackNumber = 0;
    }

    public void run() {
        while(true) {
            try {
                if (this.connectionState != TcpConnectionState.ESTABLISHED) {
                    listenForHandshake();
                }
                //receiveFile();
                socket.close();
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void listenForHandshake() throws IOException {
        this.connectionState = TcpConnectionState.LISTEN;
        while (this.connectionState != TcpConnectionState.SYN_RECEIVED) {
            if (this.isVerbose) System.out.println("Waiting for SYN...");
            TcpPacket synPacket = receivePacket();
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
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sendPacket(synAckPacket);
        if (this.isVerbose) System.out.println("Waiting for ACK...");
        TcpPacket ackPacket = receivePacket();
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

    private void receiveFile() throws IOException {
        // TODO: deal with ordering. Cache in HashMap, compare incoming packet to expected sequence number.
        // TODO: if it doesn't match, cache. If it does, check cache for more matches.
        if (this.isVerbose) System.out.println("Waiting for packet from client...");
        TcpPacket packetFromClient = receivePacket();
        if (!packetFromClient.validateChecksum()) {
            if (this.isVerbose) System.out.println("Received corrupted packet from client, sending duplicate ack");
            // send duplicate ack
        }
        // update this for each packet until we're done
        md5Digest.update(packetFromClient.getData());
        // calculate the final checksum
        byte[] md5Bytes = md5Digest.digest();
        System.out.println("MD5: " + DatatypeConverter.printHexBinary(md5Bytes));

    }

    private TcpPacket receivePacket() throws IOException {
        byte[] buf = new byte[20];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        this.clientAddress = packet.getAddress();
        this.clientPort = packet.getPort();
        return TcpPacket.deserialize(packet.getData());
    }

    private void sendPacket(TcpPacket tcpPacket) throws IOException {
        // SETS THE CHECKSUM FIELD IN THE HEADER
        tcpPacket.calculateChecksum();
        byte[] tcpPacketBytes = tcpPacket.serialize();
        DatagramPacket udpPacket = new DatagramPacket(tcpPacketBytes, tcpPacketBytes.length,
                this.clientAddress, this.clientPort);
        socket.send(udpPacket);
    }

    private TcpPacket createRstPacket() {
        TcpHeader rstHeader = new TcpHeader(0, 0, 0, 1, 0, 0, 0, 0);
        return new TcpPacket(rstHeader, new byte[0]);
    }

    private TcpPacket createSynAckPacket(int sequenceNumber, int ackNumber) {
        TcpHeader synAckHeader = new TcpHeader(sequenceNumber, ackNumber, 1, 0, 1, 0, 0, 0);
        return new TcpPacket(synAckHeader, new byte[]{});
    }
}
