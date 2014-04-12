/**
 *
 */
package gd.choir.proto.packets.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;

import gd.choir.common.AudioFile;
import gd.choir.proto.packets.DatagramPacket;
import gd.choir.proto.packets.UnknownPacketException;

/**
 * Start audio stream packet
 * @author Giulio D'Ambrosio
 */
public class PacketBegin extends DatagramPacket {
    public static final String packetCode = "BEGI";
    private static final int PACKET_LEN = 7;

    public char musicId = 0;
    public String musicTitle = "";

    /**
     * Crea il pacchetto in lettura, a partire da un pacchetto udp ricevuto
     *
     * @param rawPacket Il pacchetto udp associato
     * @throws IOException
     */
    public PacketBegin(java.net.DatagramPacket rawPacket) throws IOException {
        super(rawPacket);
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);
        if (!readCode(dis).equals(packetCode)) {
            throw new UnknownPacketException("Codice pacchetto in ingresso differente da "
                    + packetCode);
        }
        musicId = readWord(dis);
        musicTitle = readString(dis);
    }

    /**
     * Crea un pacchetto da spedire.
     *
     * @throws IOException
     */
    public PacketBegin(AudioFile audioFile, InetAddress groupAddress,
                       int groupPort) throws IOException {
        super();
        ByteArrayOutputStream out;
        DataOutputStream dos;

        musicId = audioFile.getMusicId();
        musicTitle = audioFile.getMusicTitle();

        out = new ByteArrayOutputStream(PACKET_LEN + musicTitle.length());
        dos = new DataOutputStream(out);

        writeCode(dos, packetCode);
        writeWord(dos, musicId);
        writeString(dos, musicTitle);
        dos.flush();

        rawPacket = new java.net.DatagramPacket(out.toByteArray(), out.size(), groupAddress, groupPort);
        inited = true;

    }

}
