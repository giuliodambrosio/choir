/**
 *
 */
package gd.choir.proto.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * gd.choir protocol packet.
 *
 * @author Giulio D'Ambrosio
 */
abstract public class Packet {
    /**
     * This value needs to be less than 65535 - udp header size - ip header size
     */
    public static final int MAX_PACKET_PAYLOAD_SIZE = 64 * 1024;

    /**
     * Flag: è true quando l'istanza è stata creata (in ricezione o in
     * spedizione).
     */
    protected boolean inited = false;

    /**
     * Converts a packet to a string: just for debugging purposes
     */
    public String toString() {
        return "packet: " + getClass();
    }

    public static String readPacketCode(final InputStream is) throws IOException {
        String pt = "";
        for (int i = 0; i < 4; i++) {
            pt += String.valueOf((char) is.read());
        }
        return pt;
    }

    public static void writePacketCode(final OutputStream os, final String code)
            throws IOException {
        os.write(code.getBytes(), 0, 4);
    }

    public static char read16BitsWord(final DataInputStream dis) throws IOException {
        return (char) dis.readUnsignedShort();
    }

    public static void write16BitsWord(final DataOutputStream dos, final char word)
            throws IOException {
        dos.writeShort(word);
    }

    public static String readZeroTerminatedString(final InputStream is) throws IOException {
        String res = "";
        int c;
        while ((c = is.read()) != 0) {
            res += String.valueOf((char) c);
        }
        return res;
    }

    public static void writeZeroTerminatedString(final OutputStream out, final String str)
            throws IOException {
        for (int i = 0; i < str.length(); i++) {
            out.write(str.charAt(i));
        }
        out.write(0);
    }
}
