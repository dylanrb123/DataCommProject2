import java.util.Arrays;

/**
 * Model for TCP packet object
 */
public class TcpPacket {
    private TcpHeader header;
    private byte[] data;

    /**
     * Constructs the model
     * @param header TCP header
     * @param data packet data
     */
    public TcpPacket(TcpHeader header, byte[] data) {
        this.header = header;
        this.data = data;
    }

    /**
     * Calculates and sets the IP checksum of the packet
     * @return the checksum
     */
    public int calculateChecksum() {
        int checksum = Utils.calculateIPChecksum(this.serialize());
        this.header.setChecksum(checksum);
        return checksum;
    }

    /**
     * validates the checksum of the packet. Stores the set value, zeroes it out, recalculates the checksum, compares,
     * and resets the checksum field of the header to the original value.
     * @return true if the calculated checksum matches the stored one, else false
     */
    public boolean validateChecksum() {
        int checksumFromHeader = this.header.getChecksum();
        this.header.setChecksum(0);
        int calculatedChecksum = Utils.calculateIPChecksum(this.serialize());
        this.header.setChecksum(checksumFromHeader);
        return checksumFromHeader == calculatedChecksum;
    }

    /**
     * Serialized the packet into a byte array
     * @return the serialized packet
     */
    public byte[] serialize() {
        return Utils.concatAll(this.header.serialize(), this.data);
    }

    /**
     * Deserializes a byte array into a TcpPacket object
     * @param bytes the serialized packet
     * @return the TcpPacket object
     */
    public static TcpPacket deserialize(byte[] bytes) {
        TcpHeader header = TcpHeader.deserialize(Arrays.copyOfRange(bytes, 0, 20));
        return new TcpPacket(header, Arrays.copyOfRange(bytes, 20, bytes.length));
    }

    public byte[] getData() {
        return this.data;
    }

    public TcpHeader getHeader() {
        return this.header;
    }

    @Override
    public String toString() {
        return this.header.toString() + '\n' + "Bytes:\n" + Arrays.toString(this.data);
    }
}
