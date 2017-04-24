import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    public ServerThread(int port, int maxSegmentSize, boolean isVerbose) throws IOException, NoSuchAlgorithmException {
        super("ServerThread");
        socket = new DatagramSocket(port);
        System.out.println("Listening on port " + port + "...");
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

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void listenForHandshake() throws IOException {
        this.connectionState = TcpConnectionState.LISTEN;
        while (this.connectionState != TcpConnectionState.ESTABLISHED) {
            TcpPacket packetFromClient = receivePacket();
            if (!packetFromClient.validateChecksum()) {
                if (this.isVerbose) System.out.println("Received corrupted packet from client");
                continue;
            }
            if (packetFromClient.getHeader().getIsSyn() == 1) {
                if (this.isVerbose) {
                    System.out.println("Received SYN packet from client");
                    System.out.println("Sending SYN-ACK packet...");
                }
                this.connectionState = TcpConnectionState.SYN_RECEIVED;
                // TODO: LOOK UP ACTUAL SEQUENCE/ACK NUMBERS FOR HANDSHAKE
                TcpPacket synAckPacket = createSynAckPacket(this.sequenceNumber, this.ackNumber);
                sendPacket(synAckPacket);
                if (this.isVerbose) System.out.println("Waiting for ACK...");
                packetFromClient = receivePacket();
                if (!packetFromClient.validateChecksum()) {
                    System.out.println("Received corrupted packet fromm client");
                    continue;
                }
                if (packetFromClient.getHeader().getIsAck() == 1) {
                    if (this.isVerbose) System.out.println("Received ACK from client");
                    this.connectionState = TcpConnectionState.ESTABLISHED;
                    return;
                }
            }
        }
        System.out.println("Should never get here. If we do, very bad");
    }

    private TcpPacket receivePacket() throws IOException {
        byte[] buf = new byte[this.maxSegmentSize];
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

    private TcpPacket createSynAckPacket(int sequenceNumber, int ackNumber) {
        TcpHeader synAckHeader = new TcpHeader(sequenceNumber, ackNumber, 1, 0, 1, 0, 0, 0);
        return new TcpPacket(synAckHeader, new byte[]{});
    }

    private TcpPacket createAckPacket(int sequenceNumber, int ackNumber) {
        return new TcpPacket(new TcpHeader(sequenceNumber, ackNumber, 1, 0, 0, 0, 100, 0), new byte[]{});
    }
}
