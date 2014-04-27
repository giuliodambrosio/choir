/**
 *
 */
package gd.choir.proto.packets;

import java.io.*;

import gd.choir.proto.packets.audio.PacketBegin;
import gd.choir.proto.packets.audio.PacketData;
import gd.choir.proto.packets.audio.PacketEnd;

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
        inited = true;
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
            case PacketData.packetCode:
                packet = new PacketData(rawPacket);
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
}
