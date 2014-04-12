/**
 *
 */
package gd.choir.proto.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Pacchetto generico spedito attraverso connessione tcp.
 *
 * @author Giulio D'Ambrosio
 */
public abstract class StreamPacket extends Packet {

    /**
     * Costruttore
     */
    public StreamPacket() {
        super();
    }

    /**
     * Genera un pacchetto come istanza di una delle classi che discendono da
     * questa, in base al codice letto dallo stream in ingresso.
     *
     * @param dis        Data input stream da cui leggere il prossimo pacchetto
     * @param packetCode Codice da controllare
     * @return false se il codice non è quello atteso per questa istanza
     * @throws IOException
     */
    public boolean fromStream(DataInputStream dis, String packetCode)
            throws IOException {
        String code = readCode(dis);
        if (!code.equals(packetCode)) {
            throw new UnknownPacketException("Codice pacchetto in ingresso differente da "
                    + packetCode + "(" + code + ")");
        }
        return true;
    }

    /**
     * Scrive nello stream in output la codifica di questa istanza di pacchetto
     *
     * @param dos        Data output stream su cui scrivere
     * @param packetCode Codice del pacchetto da scrivere
     * @throws IOException
     */
    public boolean toStream(DataOutputStream dos, String packetCode)
            throws IOException {
        if (!inited) {
            return false;
        }
        writeCode(dos, packetCode);
        return true;
    }

}
