import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Main client class
 */
public class Client {
    private String filePath;
    private int maxSegmentSize;
    private int timeout;
    private boolean isVerbose;
    private InetAddress serverAddress;
    private int port;
    private DatagramSocket socket;
    private int sequenceNumber;

    /**
     * Constructs the client, creates the UDP socket to talk to the server
     * @param filePath path to file to send
     * @param maxSegmentSize max segment size to send across link
     * @param timeout max time to wait for ack before resending packet
     * @param isVerbose turn on verbose mode
     * @param serverAddress address of server to talk to
     * @param port port to talk to
     * @throws UnknownHostException can't find the server address
     * @throws SocketException something weird happened making the socket
     */
    public Client(String filePath, int maxSegmentSize, int timeout, boolean isVerbose, String serverAddress, int port)
            throws UnknownHostException, SocketException {
        this.filePath = filePath;
        this.maxSegmentSize = maxSegmentSize;
        this.timeout = timeout;
        this.isVerbose = isVerbose;
        this.serverAddress = InetAddress.getByName(serverAddress);
        this.port = port;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(this.timeout);
        this.sequenceNumber = 0;

    }

    /**
     * Initiates the handshake and spins up sending and receiving threads
     * @throws IOException UDP crap
     */
    public void doTheThing() throws IOException {
        handshake();
        ClientReceiveThread receiveThread = new ClientReceiveThread();
        ClientSendThread sendThread = new ClientSendThread(this.filePath, this.maxSegmentSize, this.socket, this.serverAddress, this.port);
        receiveThread.start();
        sendThread.start();
        try {
            receiveThread.join();
            sendThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        teardown();
    }

    /**
     * Performs the three way handshake
     * @throws IOException UDP crap
     */
    private void handshake() throws IOException {
        if (this.isVerbose) System.out.println("Starting three-way handshake on client");
        TcpPacket packetFromServer = null;
        TcpPacket synPacket = this.createSynPacket(this.sequenceNumber, 0);
        if (this.isVerbose) System.out.println("Sending SYN...");
        sendPacket(synPacket);
        // sequence number increments even though no data was sent, special case
        this.sequenceNumber = 1;
        try {
            if (this.isVerbose) System.out.println("Waiting for SYN-ACK...");
            packetFromServer = receivePacketOrTimeout();
        } catch (SocketTimeoutException e) {
            if (this.isVerbose) {
                System.out.println("Timed out waiting for SYN-ACK from server");
                System.out.println("Restarting handshake");
            }
            TcpPacket rstPacket = createRstPacket();
            sendPacket(rstPacket);
            handshake();
            return;
        }
        if (packetFromServer.getHeader().getIsRst() == 1) {
            if (this.isVerbose) System.out.println("Received RST from server, restarting handshake...");
            handshake();
            return;
        }
        if (!packetFromServer.validateChecksum()) {
            if (this.isVerbose) {
                System.out.println("Received corrupt packet while waiting for SYN-ACK, throwing away...");
                System.out.println("Restarting handshake");
            }
            TcpPacket rstPacket = createRstPacket();
            sendPacket(rstPacket);
            handshake();
            return;
        }
        if (!(packetFromServer.getHeader().getIsSyn() == 1 && packetFromServer.getHeader().getIsAck() == 1)) {
            if (this.isVerbose) {
                System.out.println("Received packet with wrong CTRL, throwing away...");
                System.out.println("Restarting handshake");
            }
            TcpPacket rstPacket = createRstPacket();
            sendPacket(rstPacket);
            handshake();
            return;
        }
        // received ACK for own SYN, connection is established
        if (this.isVerbose) {
            System.out.println("Received SYN-ACK from server");
            System.out.println("Connection established on client!");
            System.out.println("Sending ACK...");
        }
        TcpPacket ackPacket = createAckPacket(this.sequenceNumber, packetFromServer.getHeader().getSequenceNumber() + 1);
        sendPacket(ackPacket);

    }

    /**
     * Receives a packet within a given timeout
     * @return the packet
     * @throws IOException if there is a timeout
     */
    private TcpPacket receivePacketOrTimeout() throws IOException {
        byte[] buf = new byte[20];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        return TcpPacket.deserialize(packet.getData());
    }

    /**
     * tears down the connection
     */
    private void teardown() {

    }

    /**
     * Send a single packet
     * @param tcpPacket packet to send
     * @throws IOException UDP stuff
     */
    private void sendPacket(TcpPacket tcpPacket) throws IOException {
        // SETS THE CHECKSUM FIELD IN THE HEADER
        tcpPacket.calculateChecksum();
        byte[] tcpPacketBytes = tcpPacket.serialize();
        DatagramPacket udpPacket = new DatagramPacket(tcpPacketBytes, tcpPacketBytes.length,
                this.serverAddress, this.port);
        socket.send(udpPacket);
    }

    /**
     * Creates an empty SYN packet
     * @param sequenceNumber sequence number to send
     * @param ackNumber ack number to send
     * @return the packet
     */
    private TcpPacket createSynPacket(int sequenceNumber, int ackNumber) {
        TcpHeader synHeader = new TcpHeader(sequenceNumber, ackNumber, 0, 0, 1, 0, 0, 0);
        return new TcpPacket(synHeader, new byte[]{});
    }

    /**
     * Creates an empty ACK packet
     * @param sequenceNumber sequence number to send
     * @param ackNumber ack number to send
     * @return the packet
     */
    private TcpPacket createAckPacket(int sequenceNumber, int ackNumber) {
        TcpHeader ackHeader = new TcpHeader(sequenceNumber, ackNumber, 1, 0, 0, 0, 0, 0);
        return new TcpPacket(ackHeader, new byte[]{});
    }

    /**
     * Creates an empty RST packet
     * @return the packet
     */
    private TcpPacket createRstPacket() {
        return new TcpPacket(new TcpHeader(0, 0, 0, 1, 0, 0, 0, 0), new byte[0]);
    }
}
