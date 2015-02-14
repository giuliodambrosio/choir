/**
 *
 */
package uk.co.dambrosio.choir.data.packet.stream;

import uk.co.dambrosio.choir.data.packet.Packet;
import uk.co.dambrosio.choir.data.packet.exceptions.UnexpectedPacketException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Giulio D'Ambrosio
 */
public abstract class StreamPacket extends Packet {
    public void fromStream(DataInputStream dis, String packetCode)
            throws IOException {
        String actualPacketCode = readPacketCode(dis);
        if (!actualPacketCode.equals(packetCode)) {
            throw new UnexpectedPacketException(packetCode,actualPacketCode);
        }
    }

    public void toStream(DataOutputStream dos, String packetCode)
            throws IOException {
        writePacketCode(dos, packetCode);
    }
}
