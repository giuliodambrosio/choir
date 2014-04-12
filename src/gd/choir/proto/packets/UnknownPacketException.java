/**
 *
 */
package gd.choir.proto.packets;

import java.io.IOException;

/**
 * L'eccezione viene lanciata quando il codice di un pacchetto non coincide con
 * il codice atteso.
 *
 * @author Giulio D'Ambrosio
 */
public class UnknownPacketException extends IOException {
    /**
     *
     */
    private static final long serialVersionUID = 7671301236182494944L;

    public UnknownPacketException(String paramString) {
        super(paramString);
    }

}
