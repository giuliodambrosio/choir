/**
 *
 */
package gd.choir.proto.packets;

import java.io.IOException;

/**
 * @author Giulio D'Ambrosio
 */
public class UnknownPacketException extends IOException {
    /**
     *
     */
    private static final long serialVersionUID = 7671301236182494944L;

    public UnknownPacketException(String receivedCode) {
        super("Unknown packet code received: " + receivedCode);
    }
}
