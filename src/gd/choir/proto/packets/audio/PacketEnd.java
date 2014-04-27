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
import gd.choir.proto.packets.UnexpectedPacketException;

/**
 * End of audio streaming packet.
 *
 * @author Giulio D'Ambrosio
 */
public class PacketEnd extends DatagramPacket {
    public static final String packetCode = "ENDF";
    private static final int PACKET_LEN = 6;
    public char musicId = 0;

    /**
     * Costruttore. Legge i dati del pacchetto dal pacchetto grezzo udp.
     *
     * @param rawPacket il pacchetto udp in ingresso
     * @throws IOException
     */
    public PacketEnd(java.net.DatagramPacket rawPacket) throws IOException {
        super(rawPacket);
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);

        String actualPacketCode = readPacketCode(dis);
        if (!actualPacketCode.equals(packetCode)) {
            throw new UnexpectedPacketException(packetCode,actualPacketCode);
        }

        musicId = read16BitsWord(dis);
    }

    /**
     * Costruttore. Genera un pacchetto udp a partire dai dati da spedire.
     *
     * @throws IOException
     */
    public PacketEnd(char musicId, InetAddress groupAddress, int groupPort)
            throws IOException {
        super();

        ByteArrayOutputStream out = new ByteArrayOutputStream(PACKET_LEN);
        DataOutputStream dos = new DataOutputStream(out);

        this.musicId = musicId;

        writePacketCode(dos, packetCode);
        write16BitsWord(dos, musicId);
        dos.flush();

        rawPacket = new java.net.DatagramPacket(out.toByteArray(), PACKET_LEN, groupAddress, groupPort);
        inited = true;
    }

    // return rawPacket.getAddress().toString()+String.valueOf(musicId);
}
