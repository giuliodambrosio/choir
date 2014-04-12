/**
 *
 */
package gd.choir.proto.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Instructs a client to play an audio file
 * @author Giulio D'Ambrosio
 */
public class PacketPlay extends StreamPacket {
    public static final String packetCode = "PLAY";
    public char musicId = 0;

    public PacketPlay() {
        super();
    }

    public PacketPlay(char musicId) {
        super();
        this.musicId = musicId;
        inited = true;
    }

    public boolean fromStream(DataInputStream dis) throws IOException {
        if (!super.fromStream(dis, packetCode)) {
            return false;
        }

        musicId = readWord(dis);
        return (inited = musicId != 0);
    }

    public boolean toStream(DataOutputStream dos) throws IOException {
        if (!(musicId != 0 && super.toStream(dos, packetCode))) {
            throw new UnknownPacketException("Codice pacchetto in ingresso differente da "
                    + packetCode);
        }
        writeWord(dos, musicId);
        return true;
    }

    public String toString() {
        return super.toString() + ",id:" + musicId;
    }

}
