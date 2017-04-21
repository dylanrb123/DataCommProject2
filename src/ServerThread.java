import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerThread extends Thread {
    private DatagramSocket socket;
    private int maxSegmentSize;

    public ServerThread(int port, int maxSegmentSize) throws IOException {
        super("ServerThread");
        socket = new DatagramSocket(port);
        System.out.println("Listening on port " + port + "...");
        this.maxSegmentSize = maxSegmentSize;
    }

    public void run() {
        while(true) {
            try {
                byte[] buf = new byte[this.maxSegmentSize];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                TcpPacket tcpPacket = TcpPacket.deserialize(packet.getData());
                System.out.println("Packet on server:");
                System.out.println(tcpPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
