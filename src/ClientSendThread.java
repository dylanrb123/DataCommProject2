import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

public class ClientSendThread extends Thread {
    private String filePath;
    private int maxSegmentSize;
    private int dataPerSegment;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private int sequenceNumber;

    public ClientSendThread(String filePath, int maxSegmentSize, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        super("SendThread");
        this.filePath = filePath;
        this.maxSegmentSize = maxSegmentSize;
        this.dataPerSegment = maxSegmentSize - 20;
        this.socket = socket;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {
        try {
            sendFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends the file over the established connection
     * @throws IOException UDP stuff
     */
    public void sendFile() throws IOException {
        Path path = Paths.get(filePath);
        byte[] fileBytes = Files.readAllBytes(path);
        int numPackets = (int) Math.ceil((double)fileBytes.length / dataPerSegment);
        // TODO: fix so it doesn't rely on padding
        byte[] fileBytesWithPadding = new byte[numPackets * dataPerSegment];
        System.arraycopy(fileBytes, 0, fileBytesWithPadding, 0, fileBytes.length);
        try {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            md5Digest.update(fileBytesWithPadding);
            byte[] digest = md5Digest.digest();
            System.out.println("MD5: " + DatatypeConverter.printHexBinary(digest));
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 0; i < numPackets; i++) {
            byte[] data = Arrays.copyOfRange(fileBytesWithPadding, i * dataPerSegment, (i + 1) * dataPerSegment);
            int isFin = i == numPackets - 1 ? 1 : 0;
            TcpPacket filePacket = createFilePacket(this.sequenceNumber, 0, isFin, data);
            sendPacket(filePacket);
        }
    }

    /**
     * Send a single packet
     * @param tcpPacket packet to send
     * @throws IOException UDP stuff
     */
    private void sendPacket(TcpPacket tcpPacket) throws IOException {
        // SETS THE CHECKSUM FIELD IN THE HEADER
        tcpPacket.calculateChecksum();
//        System.out.println(tcpPacket); // ALSO HERE?????!!!!
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        byte[] tcpPacketBytes = tcpPacket.serialize();
        DatagramPacket udpPacket = new DatagramPacket(tcpPacketBytes, tcpPacketBytes.length,
                this.serverAddress, this.serverPort);
        socket.send(udpPacket);
    }

    private TcpPacket createFilePacket(int sequenceNumber, int window, int isFin, byte[] data) {
        return new TcpPacket(new TcpHeader(sequenceNumber, 0, 0, 0, 0, isFin, window, 0), data);
    }


}
