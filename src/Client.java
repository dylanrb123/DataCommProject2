import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Client {
    private String filePath;
    private int maxSegmentSize;
    private InetAddress serverAddress;
    private int port;
    private DatagramSocket socket;

    public Client(String filePath, int maxSegmentSize, String serverAddress, int port)
            throws UnknownHostException, SocketException {
        this.filePath = filePath;
        this.maxSegmentSize = maxSegmentSize;
        this.serverAddress = InetAddress.getByName(serverAddress);
        this.port = port;
        this.socket = new DatagramSocket();
    }

    public void run() throws IOException {
        Path path = Paths.get(filePath);
        byte[] fileBytes = Files.readAllBytes(path);

        TcpHeader header = new TcpHeader(10, 20, 1, 0, 1, 0, 4, 0);
        TcpPacket tcpPacket = new TcpPacket(header, Arrays.copyOfRange(fileBytes, 0, this.maxSegmentSize));
        tcpPacket.calculateChecksum();      // SETS CHECKSUM FIELD IN HEADER
        System.out.println("Packet on client:");
        System.out.println(tcpPacket);
        byte[] tcpPacketBytes = tcpPacket.serialize();
        DatagramPacket udpPacket = new DatagramPacket(tcpPacketBytes, tcpPacketBytes.length,
                this.serverAddress, this.port);
        socket.send(udpPacket);
    }
}
