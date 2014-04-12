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
 * Rappresentazione di un pacchetto del protocollo gd.choir.
 *
 * @author Giulio D'Ambrosio
 */
public abstract class Packet {
    /**
     * Massima dimensione possibile del payload del pacchetto. minore di 65535 -
     * udp header - ip header
     */
    public static final int MAX_SIZE = 64 * 1024;

    /**
     * Flag: è true quando l'istanza è stata creata (in ricezione o in
     * spedizione).
     */
    protected boolean inited = false;

    /**
     * Costruttore generico
     */
    public Packet() {
        super();
    }

    /**
     * Produce una rappresentazione di tipo string del pacchetto ( a scopo di
     * debug )
     */
    public String toString() {
        return "packet: " + getClass();
    }

    /**
     * Legge il codice del pacchetto da un input stream.
     *
     * @throws IOException
     */
    public static String readCode(final InputStream is) throws IOException {
        String pt = "";
        for (int i = 0; i < 4; i++) {
            pt += String.valueOf((char) is.read());
        }
        return pt;
    }

    /**
     * Scrive il codice del pacchetto in un output stream.
     *
     * @param os   L'output stream
     * @param code Il codice da scrivere
     * @throws IOException
     */
    public static void writeCode(final OutputStream os, final String code)
            throws IOException {
        os.write(code.getBytes(), 0, 4);
    }

    /**
     * Legge un unsigned uword_16 di 16 bit da un input stream.
     *
     * @param dis
     * @return uword letto
     * @throws IOException
     */
    public static char readWord(final DataInputStream dis) throws IOException {
        return (char) dis.readUnsignedShort();
    }

    /**
     * Scrive un unsigned word di 16 bit in un output stream.
     *
     * @param dos  Data output stream verso cui scrivere
     * @param word uword_16 da scrivere
     * @throws IOException
     */
    public static void writeWord(final DataOutputStream dos, final char word)
            throws IOException {
        dos.writeShort(word);
    }

    /**
     * Legge un c-string da un data input stream.
     *
     * @param is Input stream da cui leggere
     * @return Stringa letta
     * @throws IOException
     */
    public static String readString(final InputStream is) throws IOException {
        String res = "";
        int c;
        while ((c = is.read()) != 0) {
            res += String.valueOf((char) c);
        }
        return res;
    }

    /**
     * Scrive una stringa codificata come c-string in un output stream.
     *
     * @param out OutputStream verso cui scrivere
     * @param str La stringa da scrivere
     * @throws IOException
     */
    public static void writeString(final OutputStream out, final String str)
            throws IOException {
        for (int i = 0; i < str.length(); i++) {
            out.write(str.charAt(i));
        }
        out.write(0);
    }
}
