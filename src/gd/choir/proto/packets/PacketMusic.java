/**
 *
 */
package gd.choir.proto.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import gd.choir.common.AudioFile;

/**
 * Informs the server about a file available for sharing.
 *
 * @author Giulio D'Ambrosio
 */
public class PacketMusic extends StreamPacket {
    public static final String packetCode = "MUSI";
    public char musicId = 0;
    public String musicTitle = "";

    public PacketMusic() {
        super();
    }

    /**
     * Creates a packet for an audio file
     */
    public PacketMusic(AudioFile audioFile) {
        super();
        musicId = audioFile.getMusicId();
        musicTitle = audioFile.getMusicTitle();
        inited = true;
    }

    /**
     * Riceve un pacchetto di questo genere da un input stream. Se il codice del
     * pacchetto non coincide con quello previsto da questa classe, la funzione
     * restituisce false, altrimenti true.
     */
    public boolean fromStream(DataInputStream dis) throws IOException {
        if (!super.fromStream(dis, packetCode)) {
            return false;
        }
        musicId = readWord(dis);
        musicTitle = readString(dis);
        return (inited = musicId != 0 && !musicTitle.equals(""));
    }

    public boolean toStream(DataOutputStream dos) throws IOException {
        if (!(musicId != 0 && !musicTitle.equals("") && super.toStream(dos, packetCode))) {
            return false;
        }
        writeWord(dos, musicId);
        writeString(dos, musicTitle);
        return true;
    }

    public String toString() {
        return super.toString() + ", id:" + musicId + ", title:" + musicTitle;
    }

}
