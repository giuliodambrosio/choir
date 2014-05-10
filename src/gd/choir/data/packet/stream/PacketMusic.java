/**
 *
 */
package gd.choir.data.packet.stream;

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
    }

    public void fromStream(DataInputStream dis) throws IOException {
        super.fromStream(dis, packetCode);
        musicId = read16BitsWord(dis);
        musicTitle = readZeroTerminatedString(dis);
    }

    public void toStream(DataOutputStream dos) throws IOException {
        super.toStream(dos, packetCode);
        write16BitsWord(dos, musicId);
        writeZeroTerminatedString(dos, musicTitle);
    }

    public String toString() {
        return super.toString() + ", id:" + musicId + ", title:" + musicTitle;
    }

}
