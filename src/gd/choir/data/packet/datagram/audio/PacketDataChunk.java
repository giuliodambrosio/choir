/**
 *
 */
package gd.choir.data.packet.datagram.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;

import gd.choir.data.packet.datagram.DatagramPacket;
import gd.choir.data.packet.exceptions.UnexpectedPacketException;

/**
 * Audio frame packet
 * @author Giulio D'Ambrosio
 */
public class PacketDataChunk extends DatagramPacket {
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
    public PacketDataChunk(java.net.DatagramPacket rawPacket) throws IOException {
        super(rawPacket);
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);


        String actualPacketCode = readPacketCode(dis);
        if (!actualPacketCode.equals(packetCode)) {
            throw new UnexpectedPacketException(packetCode,actualPacketCode);
        }

        musicId = read16BitsWord(dis);
        totLength = read16BitsWord(dis);
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
    public PacketDataChunk(char musicId, byte[] audioData, char size,
                           InetAddress groupAddress, int groupPort) throws IOException {
        super();
        byte[] buf;
        ByteArrayOutputStream out = new ByteArrayOutputStream(totLength = (char) (PACKET_LEN + size));
        DataOutputStream dos = new DataOutputStream(out);

        this.musicId = musicId;
        this.audioData = audioData;

        writePacketCode(dos, packetCode);
        write16BitsWord(dos, musicId);
        write16BitsWord(dos, totLength);
        dos.write(audioData, 0, size);
        out.flush();

        buf = out.toByteArray();
        rawPacket = new java.net.DatagramPacket(buf, buf.length, groupAddress, groupPort);
    }
}
