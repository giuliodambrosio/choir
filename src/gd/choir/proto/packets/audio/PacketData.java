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

import gd.choir.proto.packets.DatagramPacket;
import gd.choir.proto.packets.UnknownPacketException;

/**
 * Audio frame packet
 * @author Giulio D'Ambrosio
 */
public class PacketData extends DatagramPacket {
    public static final String packetCode = "DATA";
    private static final char PACKET_LEN = 8;

    protected char totLength = PACKET_LEN;
    public char musicId = 0;
    public byte[] audioData = null;

    /**
     * Crea il pacchetto in lettura, a partire da un pacchetto udp ricevuto
     *
     * @param rawPacket Il pacchetto udp associato
     * @throws IOException
     */
    public PacketData(java.net.DatagramPacket rawPacket) throws IOException {
        super(rawPacket);
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);

        if (!readCode(dis).equals(packetCode)) {
            throw new UnknownPacketException("Codice pacchetto in ingresso differente da "
                    + packetCode);
        }
        musicId = readWord(dis);
        totLength = readWord(dis);
        audioData = new byte[totLength - PACKET_LEN];
        //noinspection ResultOfMethodCallIgnored
        in.read(audioData);
    }

    /**
     * Crea un pacchetto da spedire.
     *
     * @param musicId      id del brano
     * @param audioData    buffer dei dati del file audio
     * @param size         numero di bytes del buffer audioData da spedire
     * @param groupPort    porta del gruppo multicast
     * @param groupAddress indirizzo del gruppo multicast
     * @throws IOException
     */
    public PacketData(char musicId, byte[] audioData, char size,
                      InetAddress groupAddress, int groupPort) throws IOException {
        super();
        byte[] buf;
        ByteArrayOutputStream out = new ByteArrayOutputStream(totLength = (char) (PACKET_LEN + size));
        DataOutputStream dos = new DataOutputStream(out);

        this.musicId = musicId;
        this.audioData = audioData;

        writeCode(dos, packetCode);
        writeWord(dos, musicId);
        writeWord(dos, totLength);
        dos.write(audioData, 0, size);
        out.flush();

        buf = out.toByteArray();
        rawPacket = new java.net.DatagramPacket(buf, buf.length, groupAddress, groupPort);
        inited = true;
    }
}
