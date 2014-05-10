/**
 *
 */
package gd.choir.data.packet.datagram;

import java.io.*;
import java.net.InetAddress;

import gd.choir.data.packet.Packet;
import gd.choir.data.packet.datagram.audio.PacketBegin;
import gd.choir.data.packet.datagram.audio.PacketDataChunk;
import gd.choir.data.packet.datagram.audio.PacketEnd;
import gd.choir.data.packet.exceptions.UnexpectedPacketException;
import gd.choir.data.packet.exceptions.UnknownPacketException;

/**
 * Pacchetto generico spedito attraverso connessione udp.
 *
 * @author Giulio D'Ambrosio
 */
abstract public class DatagramPacket extends Packet {
    /**
     * Eventuale pacchetto udp generato o ricevuto.
     */
    protected java.net.DatagramPacket rawPacket = null;

    /**
     * Costruttore
     */
    public DatagramPacket() {
        super();
    }

    /**
     * Costruttore.
     *
     * @param rawPacket il pacchetto udp ricevuto, a cui associare questa istanza
     */
    public DatagramPacket(java.net.DatagramPacket rawPacket) {
        super();
        this.rawPacket = rawPacket;
    }

    /**
     * @return il pacchetto udp a cui è associata questa istanza
     */
    public java.net.DatagramPacket getRawPacket() {
        return rawPacket;
    }

    /**
     * Genera un pacchetto discendente da questa classe, in base al codice
     * (primi 4 bytes del pacchetto udp)
     *
     * @param rawPacket Packet to translate
     * @return Istanza di una delle classi discendenti da questa
     * @throws IOException
     */
    static public DatagramPacket fromDatagram(java.net.DatagramPacket rawPacket)
            throws IOException {
        DatagramPacket packet;
        String packetCode = DatagramPacket.peekPacketCode(rawPacket);
        switch (packetCode) {
            case PacketJoin.packetCode:
                packet = new PacketJoin(rawPacket);
                break;
            case PacketHello.packetCode:
                packet = new PacketHello(rawPacket);
                break;
            case PacketBegin.packetCode:
                packet = new PacketBegin(rawPacket);
                break;
            case PacketDataChunk.packetCode:
                packet = new PacketDataChunk(rawPacket);
                break;
            case PacketEnd.packetCode:
                packet = new PacketEnd(rawPacket);
                break;
            default:
                throw new UnknownPacketException(packetCode);
        }
        return packet;
    }

    static public String peekPacketCode(java.net.DatagramPacket rawPacket) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);
        return readPacketCode(dis);
    }

    /**
     * This packet is used to join a group. The client sends this packet and, if any server is listening,
     * a PacketHello packet is sent back.
     *
     * @author Giulio D'Ambrosio
     */
    public static class PacketJoin extends DatagramPacket {
        public static final String packetCode = "JOIN";
        private static final int PACKET_LEN = 4;

        /**
         * Crea il pacchetto in lettura, a partire da un pacchetto udp ricevuto
         *
         * @param rawPacket Il pacchetto udp associato
         * @throws java.io.IOException
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
         * @throws java.io.IOException
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
        }

    }
}
