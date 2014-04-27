/**
 *
 */
package gd.choir.proto.packets;

import java.net.*;
import java.io.*;

/**
 * A welcome packet. When the server hears a join request answers back with this packet
 * to accept the join request.
 *
 * @author Giulio D'Ambrosio
 */
public class PacketHello extends DatagramPacket {
    public static final String packetCode = "HELO";
    public int PACKET_LEN = 10;

    public InetAddress serverAddress = null;
    public char serverPort = 0;

    public PacketHello(java.net.DatagramPacket rawPacket) throws IOException {
        super(rawPacket);
        byte rawAddress[] = new byte[4];
        String rc;
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);

        String actualPacketCode = readPacketCode(dis);
        if (!actualPacketCode.equals(packetCode)) {
            throw new UnexpectedPacketException(packetCode,actualPacketCode);
        }

        //noinspection ResultOfMethodCallIgnored
        dis.read(rawAddress, 0, 4);
        serverAddress = InetAddress.getByAddress(rawAddress);
        serverPort = read16BitsWord(dis);
    }

    public PacketHello(InetAddress serverAddress, char serverPort,
                       InetAddress groupAddress, char groupPort) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(PACKET_LEN);
        DataOutputStream dos = new DataOutputStream(out);

        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        writePacketCode(dos, packetCode);
        dos.write(serverAddress.getAddress(), 0, 4);
        write16BitsWord(dos, serverPort);
        dos.flush();
        rawPacket = new java.net.DatagramPacket(out.toByteArray(), PACKET_LEN, groupAddress, groupPort);
        inited = true;
    }
}
