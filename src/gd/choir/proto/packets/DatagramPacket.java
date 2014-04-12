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
public class DatagramPacket extends Packet {
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
        DatagramPacket p;
        String pt = DatagramPacket.peekCode(rawPacket);
        switch (pt) {
            case PacketJoin.packetCode:
                p = new PacketJoin(rawPacket);
                break;
            case PacketHello.packetCode:
                p = new PacketHello(rawPacket);
                break;
            case PacketBegin.packetCode:
                p = new PacketBegin(rawPacket);
                break;
            case PacketData.packetCode:
                p = new PacketData(rawPacket);
                break;
            case PacketEnd.packetCode:
                p = new PacketEnd(rawPacket);
                break;
            default:
                throw new UnknownPacketException("Unknown packet code ("
                        + pt + "[" + pt.length() + "])");
        }
        return p;
    }

    /**
     * Legge il codice da un pacchetto udp in arrivo.
     *
     * @param rawPacket Packet to inspect
     * @return Stringa contenente il codice
     * @throws IOException
     */
    static public String peekCode(java.net.DatagramPacket rawPacket) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(rawPacket.getData());
        DataInputStream dis = new DataInputStream(in);
        return readCode(dis);
    }

}
