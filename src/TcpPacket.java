import java.util.Arrays;

public class TcpPacket {
    private TcpHeader header;
    private byte[] data;

    public TcpPacket(TcpHeader header, byte[] data) {
        this.header = header;
        this.data = data;
    }

    public byte[] getData() {
        return this.data;
    }

    public TcpHeader getHeader() {
        return this.header;
    }

    public int calculateChecksum() {
        int checksum = Utils.calculateIPChecksum(this.serialize());
        this.header.setChecksum(checksum);
        return checksum;
    }

    public byte[] serialize() {
        return Utils.concatAll(this.header.serialize(), this.data);
    }

    public boolean validateChecksum() {
        int checksumFromHeader = this.header.getChecksum();
        this.header.setChecksum(0);
        int calculatedChecksum = Utils.calculateIPChecksum(this.serialize());
        this.header.setChecksum(checksumFromHeader);
        return checksumFromHeader == calculatedChecksum;
    }

    public static TcpPacket deserialize(byte[] bytes) {
        TcpHeader header = TcpHeader.deserialize(Arrays.copyOfRange(bytes, 0, 20));
        return new TcpPacket(header, Arrays.copyOfRange(bytes, 20, bytes.length));
    }

    @Override
    public String toString() {
        return this.header.toString() + '\n' + "Bytes:\n" + Arrays.toString(this.data);
    }
}
