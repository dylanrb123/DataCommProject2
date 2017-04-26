import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Client {
    private String filePath;
    private int maxSegmentSize;
    private int timeout;
    private boolean isVerbose;
    private InetAddress serverAddress;
    private int port;
    private DatagramSocket socket;
    private int sequenceNumber;
    private TcpConnectionState connectionState;

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
        this.connectionState = TcpConnectionState.CLOSED;

    }

    public void doTheThing() throws IOException {
        handshake();
        ClientReceiveThread receiveThread = new ClientReceiveThread();
        ClientSendThread sendThread = new ClientSendThread();
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

    private void handshake() throws IOException {
        if (this.isVerbose) System.out.println("Starting three-way handshake on client");
        TcpPacket packetFromServer = null;
        TcpPacket synPacket = this.createSynPacket(this.sequenceNumber, 0);
        if (this.isVerbose) System.out.println("Sending SYN...");
        sendPacket(synPacket);
        // sequence number increments even though no data was sent, special case
        this.sequenceNumber = 1;
        this.connectionState = TcpConnectionState.SYN_SENT;
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
        this.connectionState = TcpConnectionState.ESTABLISHED;
        TcpPacket ackPacket = createAckPacket(this.sequenceNumber, packetFromServer.getHeader().getSequenceNumber() + 1);
        sendPacket(ackPacket);

    }

    private TcpPacket receivePacketOrTimeout() throws IOException {
        byte[] buf = new byte[20];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        return TcpPacket.deserialize(packet.getData());
    }

    private void teardown() {

    }

    public void sendFile() throws IOException {
        Path path = Paths.get(filePath);
        byte[] fileBytes = Files.readAllBytes(path);
//        for (int i = 0; i < fileBytes.length; i += (this.maxSegmentSize - 20)) {
            // set checksum to zero initially
            TcpHeader header = new TcpHeader(10, 20, 1, 0, 1, 0, 4, 0);
            // make the packet, header plus bytes of data up to max segment size
            int dataLength = Math.min(this.maxSegmentSize - 20, fileBytes.length);
            TcpPacket tcpPacket = new TcpPacket(header, Arrays.copyOfRange(fileBytes, 0, dataLength));
            tcpPacket.calculateChecksum();      // SETS CHECKSUM FIELD IN HEADER
            sendPacket(tcpPacket);
//        }
    }

    private void sendPacket(TcpPacket tcpPacket) throws IOException {
        // SETS THE CHECKSUM FIELD IN THE HEADER
        tcpPacket.calculateChecksum();
        byte[] tcpPacketBytes = tcpPacket.serialize();
        DatagramPacket udpPacket = new DatagramPacket(tcpPacketBytes, tcpPacketBytes.length,
                this.serverAddress, this.port);
        socket.send(udpPacket);
    }

    private TcpPacket createSynPacket(int sequenceNumber, int ackNumber) {
        TcpHeader synHeader = new TcpHeader(sequenceNumber, ackNumber, 0, 0, 1, 0, 0, 0);
        return new TcpPacket(synHeader, new byte[]{});
    }

    private TcpPacket createAckPacket(int sequenceNumber, int ackNumber) {
        TcpHeader ackHeader = new TcpHeader(sequenceNumber, ackNumber, 1, 0, 0, 0, 0, 0);
        return new TcpPacket(ackHeader, new byte[]{});
    }

    private TcpPacket createRstPacket() {
        return new TcpPacket(new TcpHeader(0, 0, 0, 1, 0, 0, 0, 0), new byte[0]);
    }
}
