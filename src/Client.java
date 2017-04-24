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
    private int ackNumber;
    private TcpConnectionState connectionState;
    private long elapsedTime;

    public Client(String filePath, int maxSegmentSize, int timeout, boolean isVerbose, String serverAddress, int port)
            throws UnknownHostException, SocketException {
        this.filePath = filePath;
        this.maxSegmentSize = maxSegmentSize;
        this.timeout = timeout;
        this.isVerbose = isVerbose;
        this.serverAddress = InetAddress.getByName(serverAddress);
        this.port = port;
        this.socket = new DatagramSocket();
        this.sequenceNumber = 0;
        this.ackNumber = 0;
        this.connectionState = TcpConnectionState.CLOSED;
        this.elapsedTime = 0;
    }

    public void handshake() throws IOException {
        if (this.isVerbose) System.out.println("Starting three-way handshake on client");
        long startTime = System.currentTimeMillis();

        TcpPacket synPacket = this.createSynPacket(this.sequenceNumber, this.ackNumber);
        if (this.isVerbose) System.out.println("Sending SYN");
        sendPacket(synPacket);
        this.connectionState = TcpConnectionState.SYN_SENT;
        while (this.connectionState != TcpConnectionState.ESTABLISHED && elapsedTime < this.timeout) {
            if (this.isVerbose) System.out.println("Waiting for response from server...");
            TcpPacket packetFromServer = receivePacket();
            if (!packetFromServer.validateChecksum()) {
                if (this.isVerbose) System.out.println("Received corrupted packet from server, throwing away...");
                continue;
            }
            if (packetFromServer.getHeader().getIsAck() == 1 && packetFromServer.getHeader().getIsSyn() == 1) {
                // received ack, reset elapsed time
                this.elapsedTime = 0;
                if (this.isVerbose) System.out.println("Received SYN-ACK from server, sending ACK");
                TcpPacket ackPacket = createAckPacket(this.sequenceNumber, this.ackNumber);
                sendPacket(ackPacket);
                this.connectionState = TcpConnectionState.ESTABLISHED;
                if (this.isVerbose) System.out.println("TCP connection established on client!");
                return;
            }
            elapsedTime = System.currentTimeMillis() - startTime;
        }
        // If we get here, timeout expired
    }

    public void sendFile() throws IOException {
        Path path = Paths.get(filePath);
        byte[] fileBytes = Files.readAllBytes(path);
        for (int i = 0; i < fileBytes.length; i += (this.maxSegmentSize - 20)) {
            // set checksum to zero initially
            TcpHeader header = new TcpHeader(10, 20, 1, 0, 1, 0, 4, 0);
            // make the packet, header plus bytes of data up to max segment size
            int dataLength = Math.min(this.maxSegmentSize - 20, fileBytes.length);
            TcpPacket tcpPacket = new TcpPacket(header, Arrays.copyOfRange(fileBytes, 0, dataLength));
            tcpPacket.calculateChecksum();      // SETS CHECKSUM FIELD IN HEADER
            System.out.println("Packet in client: ");
            System.out.println(tcpPacket);
            sendPacket(tcpPacket);
        }
    }

    private TcpPacket receivePacket() throws IOException {
        byte[] buf = new byte[this.maxSegmentSize];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        return TcpPacket.deserialize(packet.getData());
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
}
