/**
 *
 */
package uk.co.dambrosio.choir.data.packet.datagram.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;

import uk.co.dambrosio.choir.common.AudioFile;
import uk.co.dambrosio.choir.data.packet.datagram.DatagramPacket;
import uk.co.dambrosio.choir.data.packet.exceptions.UnexpectedPacketException;

/**
 * Start audio stream packet
 *
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

        String actualPacketCode = readPacketCode(dis);
        if (!actualPacketCode.equals(packetCode)) {
            throw new UnexpectedPacketException(packetCode, actualPacketCode);
        }

        musicId = read16BitsWord(dis);
        musicTitle = readZeroTerminatedString(dis);
    }

    /**
     * Crea un pacchetto da spedire.
     *
     * @throws IOException
     */
    public PacketBegin(
            AudioFile audioFile,
            InetAddress groupAddress,
            int groupPort
    ) throws IOException {
        super();
        ByteArrayOutputStream out;
        DataOutputStream dos;

        musicId = audioFile.getMusicId();
        musicTitle = audioFile.getMusicTitle();

        out = new ByteArrayOutputStream(PACKET_LEN + musicTitle.length());
        dos = new DataOutputStream(out);

        writePacketCode(dos, packetCode);
        write16BitsWord(dos, musicId);
        writeZeroTerminatedString(dos, musicTitle);
        dos.flush();

        rawPacket = new java.net.DatagramPacket(out.toByteArray(), out.size(), groupAddress, groupPort);
    }
}
