/**
 *
 */
package uk.co.dambrosio.choir.data.packet.stream;

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
    }

    public void fromStream(DataInputStream dis) throws IOException {
        super.fromStream(dis, packetCode);

        musicId = read16BitsWord(dis);
    }

    public void toStream(DataOutputStream dos) throws IOException {
        super.toStream(dos, packetCode);
        write16BitsWord(dos, musicId);
    }

    public String toString() {
        return super.toString() + ",id:" + musicId;
    }

}
