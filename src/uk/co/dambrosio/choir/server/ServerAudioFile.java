/**
 *
 */
package uk.co.dambrosio.choir.server;

import java.io.IOException;

import uk.co.dambrosio.choir.common.AudioFile;

/**
 * Server-side audio file abstraction
 *
 * @author Giulio D'Ambrosio
 */
public final class ServerAudioFile extends AudioFile {
    private ServerClientHandler ownerClientHandler;

    private boolean isBeingStreamed = false;

    public ServerAudioFile(final char musicId, final String musicTitle,
                           final ServerClientHandler ownerClientHandler) {
        super();
        if (ownerClientHandler == null) {
            throw new NullPointerException("Owner for the file must be set");
        }
        setMusicId(musicId);
        this.ownerClientHandler = ownerClientHandler;
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
        result = hashCode(getOwnerClientHandler().getSocket().getInetAddress(), musicId);
        return result & 0xffff;
    }

    /**
     * @return L'istanza del gestore delle comunicazioni del client a cui
     * appartiene questo brano
     */
    public ServerClientHandler getOwnerClientHandler() {
        return ownerClientHandler;
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
     * Chiede al thread che si occupa di comunicare con il client (ownerClientHandler
     * {@link ServerClientHandler}) di spedire un pacchetto play con l'id di
     * questa istanza.
     *
     * @throws IOException
     */
    public void requestClientForAudioStreaming() throws Exception {
        ownerClientHandler.requestClientForAudioStreaming(this);
    }

    /**
     * Notifica al thread che si occupa di comunicare con il client (ownerClientHandler
     * {@link ServerClientHandler}) che lo streaming di questo brano è
     * terminato.
     *
     * @throws Exception
     */
    public void streamingEnded() throws Exception {
        isBeingStreamed = false;
        ownerClientHandler.streamingEnded(this);
    }
}
