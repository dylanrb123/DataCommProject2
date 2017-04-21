import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class Utils {
    /**
     * Calculates the IP Checksum of the given byte array
     * @param buff the buffer
     * @return the checksum
     */
    public static int calculateIPChecksum(byte[] buff) {
        int i = 0;
        int sum = 0;
        int length = buff.length;
        while (length > 0) {
            sum += (buff[i++] & 0xFF) << 8;
            if ((--length) == 0) {
                break;
            }
            sum += (buff[i++] & 0xFF);
            --length;
        }

        return (~((sum & 0xFFFF) + (sum >> 16))) & 0xFFFF;
    }

    /**
     * Converts an int to a byte array in little endian order
     * @param in int to convert
     * @return resulting byte array
     */
    public static byte[] intToByteArrayBigEndian(int in) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(in).array();
    }

    /**
     * Converts a little endian byte array to an int
     * @param in byte array to convert
     * @return resulting int
     */
    public static int byteArrayToIntBigEndian(byte[] in) {
        return ByteBuffer.wrap(in).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    /**
     * Concatenates any number of byte arrays in order
     * @param first first array
     * @param rest rest of the arrays
     * @return concatenated arrays
     */
    public static byte[] concatAll(byte[] first, byte[]... rest) {
        int totalLength = first.length;
        for (byte[] array : rest) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (byte[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

}
