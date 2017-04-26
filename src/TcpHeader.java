import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;

public class TcpHeader {
    private int sourcePort = 0;         // ALWAYS ZERO
    private int destinationPort = 0;    // ALWAYS ZERO
    private int sequenceNumber;
    private int ackNumber;
    private int dataOffset = 0;         // ALWAYS ZERO
    private int isUrgent = 0;           // ALWAYS ZERO
    private int isAck;
    private int isPush = 0;             // ALWAYS ZERO
    private int isRst;
    private int isSyn;
    private int isFin;
    private int window;
    private int checksum;
    private int urgentPointer = 0;      // ALWAYS ZERO

    public TcpHeader(int sequenceNumber, int ackNumber, int isAck, int isRst,
                     int isSyn, int isFin, int window, int checksum) {
        this.sequenceNumber = sequenceNumber;
        this.ackNumber = ackNumber;
        this.isAck = isAck;
        this.isRst = isRst;
        this.isSyn = isSyn;
        this.isFin = isFin;
        this.window = window;
        this.checksum = checksum;
    }

    public byte[] serialize() {
        int headerRowOne = (this.sourcePort << 16) | this.destinationPort;
        int headerRowTwo = this.sequenceNumber;
        int headerRowThree = this.ackNumber;
        int headerRowFour = (this.dataOffset << 28) | (this.isUrgent << 21) | (this.isAck << 20) |
                (this.isPush << 19) | (this.isRst << 18) | (this.isSyn << 17) | (this.isFin << 16) | this.window;
        int headerRowFive = (this.checksum << 16) | this.urgentPointer;
        byte[] firstRowBytes = Utils.intToByteArrayBigEndian(headerRowOne);
        byte[] secondRowBytes = Utils.intToByteArrayBigEndian(headerRowTwo);
        byte[] thirdRowBytes = Utils.intToByteArrayBigEndian(headerRowThree);
        byte[] fourthRowBytes = Utils.intToByteArrayBigEndian(headerRowFour);
        byte[] fifthRowBytes = Utils.intToByteArrayBigEndian(headerRowFive);

        return Utils.concatAll(firstRowBytes, secondRowBytes, thirdRowBytes, fourthRowBytes, fifthRowBytes);
    }

    public static TcpHeader deserialize(byte[] headerBytes) {
        int sequenceNum = Utils.byteArrayToIntBigEndian(Arrays.copyOfRange(headerBytes, 4, 8));
        int ackNum = Utils.byteArrayToIntBigEndian(Arrays.copyOfRange(headerBytes, 8, 12));
        int rowFour = Utils.byteArrayToIntBigEndian(Arrays.copyOfRange(headerBytes, 12, 16));
        int rowFive = Utils.byteArrayToIntBigEndian(Arrays.copyOfRange(headerBytes, 16, 20));
        int dataOffset = rowFour >>> 28;         // NOT USED
        // mask off first 10 bits, shift right
        int flags = (rowFour >>> 16) & 0x3F;
        // mask off first 16, shift right
        int congestionWindow = rowFour & 0xFFFF;
        int checksum = rowFive >>> 16;
        int urgentPointer = rowFive & 0xFFFF;   // NOT USED
        // decode the flags
        int isUrgent = flags >>> 5;              // NOT USED
        int isAck = (flags >>> 4) & 0x1;
        int isPush = (flags >>> 3) & 0x1;        // NOT USED
        int isRst = (flags >>> 2) & 0x1;
        int isSyn = (flags >>> 1) & 0x1;
        int isFin = flags & 0x1;

        return new TcpHeader(sequenceNum, ackNum, isAck, isRst, isSyn, isFin, congestionWindow, checksum);
    }

    @Override
    public String toString() {

        return "Source port: " +
                this.sourcePort +
                "\n" +
                "Destination port: " +
                this.destinationPort +
                "\n" +
                "Sequence Number: " +
                this.sequenceNumber +
                "\n" +
                "Ack Number: " +
                this.ackNumber +
                "\n" +
                "Data Offset: " +
                this.dataOffset +
                "\n" +
                "Flags: " +
                "URG:" +
                this.isUrgent +
                "," +
                "ACK:" +
                this.isAck +
                "," +
                "PSH:" +
                this.isPush +
                "," +
                "RST:" +
                this.isRst +
                "," +
                "SYN:" +
                this.isSyn +
                "," +
                "FIN:" +
                this.isFin +
                "\n" +
                "Window: " +
                this.window +
                "\n" +
                "Checksum: " +
                this.checksum +
                "\n" +
                "Urgent Pointer: " +
                this.urgentPointer +
                "\n";
    }
    public void setChecksum(int checksum) {
        this.checksum = checksum;
    }

    public int getChecksum() {
        return this.checksum;
    }

    public int getIsAck() {
        return this.isAck;
    }

    public int getIsSyn() {
        return this.isSyn;
    }

    public int getIsFin() {
        return this.isFin;
    }

    public int getIsRst() {
        return isRst;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getAckNumber() {
        return ackNumber;
    }
}
