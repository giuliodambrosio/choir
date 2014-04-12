/**
 *
 */
package gd.choir.server;

import java.io.IOException;

import gd.choir.common.AudioFile;

/**
 * Rappresentazione di una risorsa audio (lato server).
 *
 * @author Giulio D'Ambrosio
 */
public final class ServerAudioFile extends AudioFile {
    /**
     * Eventuale istanza del task associato a questo brano.
     */
    private ServerClientHandler owner = null;

    /**
     * Flag: se true, questo brano è attualmente in streaming.
     */
    private boolean isBeingStreamed = false;

    /**
     * Costruttore. La versione lato server del file audio, include un'istanza
     * del gestore delle comunicazioni con il singolo client (owner).
     */
    public ServerAudioFile(final char musicId, final String musicTitle,
                           final ServerClientHandler owner) {
        super(musicId);
        if (owner == null) {
            throw new NullPointerException("Owner for the file must be set");
        }
        this.owner = owner;
        this.musicTitle = musicTitle;
    }

    /**
     * Genera il codice hash di questo brano. La versione lato server include
     * l'indirizzo del client nel computo dell'id.
     *
     * @return l'intero corrispondente al codice hash di questa istanza.
     */
    @Override
    public int hashCode() {
        int result;
        result = hashCode(getOwner().getSocket().getInetAddress(), musicId);
        return result & 0xffff;
    }

    /**
     * @return L'istanza del gestore delle comunicazioni del client a cui
     * appartiene questo brano
     */
    public ServerClientHandler getOwner() {
        return owner;
    }

    /**
     * Marca questo brano come in corso di streaming.
     */
    public void setBeingStreamed() {
        isBeingStreamed = true;
    }

    /**
     * Verifica è in corso lo streaming di questo brano.
     *
     * @return true se il brano è attualmente in streaming
     */
    public boolean isBeingStreamed() {
        return isBeingStreamed;
    }

    /**
     * Chiede al thread che si occupa di comunicare con il client (owner
     * {@link ServerClientHandler}) di spedire un pacchetto play con l'id di
     * questa istanza.
     *
     * @return true se la comunicazione è riuscita
     * @throws IOException
     */
    public boolean startStreaming() throws IOException {
        return owner.startStreaming(this);
    }

    /**
     * Notifica al thread che si occupa di comunicare con il client (owner
     * {@link ServerClientHandler}) che lo streaming di questo brano è
     * terminato.
     *
     * @throws Exception
     */
    public void streamingEnded() throws Exception {
        isBeingStreamed = false;
        owner.streamingEnded(this);
    }
}
