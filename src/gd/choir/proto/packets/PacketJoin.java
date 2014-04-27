/**
 *
 */
package gd.choir.proto.packets;

import java.net.*;
import java.io.*;

/**
 * This packet is used to join a group. The client sends this packet and, if any server is listening,
 * a PacketHello packet is sent back.
 *
 * @author Giulio D'Ambrosio
 */
public class PacketJoin extends DatagramPacket {
    public static final String packetCode = "JOIN";
    private static final int PACKET_LEN = 4;

    /**
     * Crea il pacchetto in lettura, a partire da un pacchetto udp ricevuto
     *
     * @param rawPacket Il pacchetto udp associato
     * @throws IOException
     */
    public PacketJoin(java.net.DatagramPacket rawPacket) throws IOException {
        super(rawPacket);
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);

        String actualPacketCode = readPacketCode(dis);
        if (!actualPacketCode.equals(packetCode)) {
            throw new UnexpectedPacketException(packetCode,actualPacketCode);
        }
    }

    /**
     * Crea un pacchetto di richiesta inserimento nel gruppo di ascolto.
     *
     * @param groupAddress Indirizzo del gruppo multicast
     * @param groupPort    Porta del gruppo multicast
     * @throws IOException
     */
    public PacketJoin(InetAddress groupAddress, int groupPort)
            throws IOException {
        byte[] buf;
        ByteArrayOutputStream out = new ByteArrayOutputStream(PACKET_LEN);
        DataOutputStream dos = new DataOutputStream(out);

        writePacketCode(dos, packetCode);
        dos.flush();
        buf = out.toByteArray();

        rawPacket = new java.net.DatagramPacket(buf, buf.length, groupAddress, groupPort);
        inited = true;
    }

}
