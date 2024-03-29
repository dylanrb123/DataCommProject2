import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * App class for TPC implementation
 */
public class App {
    @Parameter(names = {"-c", "--client"}, description = "doTheThing as the client")
    private boolean isClient = false;

    @Parameter(names = {"-s", "--server"}, description = "doTheThing as the server")
    private boolean isServer = false;

    @Parameter(names = {"-f", "--file"}, description = "specify file for client to send")
    private String filePath = "";

    @Parameter(names = {"-t", "--timeout"}, description = "timeout in milliseconds for retransmit timer")
    private int timeout = 1000;

    @Parameter(names = {"-v", "--verbose"}, description = "output detailed diagnostics")
    private boolean isVerbose = false;

    @Parameter(description = "server port and client port")
    private List<String> params = new ArrayList<>();

    /**
     * Parses args and creates the client or server objects, as specified by the args
     * @param args command line args
     * @throws NoSuchAlgorithmException this shouldn't happen
     */
    public static void main(String[] args) throws NoSuchAlgorithmException {
        App app = new App();
        new JCommander(app, args);
        final int maxSegmentSize = 1020;
        if (app.isServer) {
            try {
                new Server(Integer.parseInt(app.params.get(0)), maxSegmentSize, app.isVerbose).doTheThing();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (app.isClient) {
            try {
                Client client = new Client(app.filePath, maxSegmentSize, app.timeout, app.isVerbose, app.params.get(0),
                        Integer.parseInt(app.params.get(1)));
                client.doTheThing();
            } catch (UnknownHostException e) {
                System.err.println("Couldn't connect to host");
                e.printStackTrace();
            } catch (SocketException e) {
                System.err.println("Unable to open socket");
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("Couldn't open file");
                e.printStackTrace();
            }
        }
    }
}
