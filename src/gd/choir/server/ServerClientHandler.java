/**
 *
 */
package gd.choir.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Vector;

import gd.choir.proto.packets.PacketMusic;
import gd.choir.proto.packets.PacketPlay;
import gd.choir.proto.packets.UnknownPacketException;

/**
 * @author Giulio D'Ambrosio
 */
public class ServerClientHandler implements Runnable {
    /**
     * Timeout del socket tcp usato per comunicare con il client. Al verificarsi
     * del timeout, viene controllato lo stato del flag {@link #alive} e se
     * questo vale false, il thread termina.
     */
    private static final int SOCK_TIMEOUT = 4 * 1000;

    /**
     * Flag: se false il thread che esegue questa istanza è chiuso o in
     * chiusura.
     */
    private boolean alive = true;

    /**
     * Indirizzo ip del client associato a questa istanza.
     */
    private InetAddress clientAddress;

    /**
     * Porta del client associato a questa istanza.
     */
    private int clientPort;

    /**
     * Socket connesso al client associato a questa istanza
     */
    private Socket sock;

    /**
     * Input stream derivato da {@link #sock}.
     */
    private DataInputStream dis = null;

    /**
     * Brano (eventualmente null) attualmente in streaming presso il client.
     */
    private ServerAudioFile isPlaying = null;

    /**
     * Istanza del gestore della riproduzione dei brani dei client.
     */
    private ServerPlayer playerServer = null;

    /**
     * Thread attualmente in esecuzione associato a questa istanza.
     */
    private Thread runningThread;

    /**
     * Lista dei brani disponibili presso il client.
     */
    private Vector<ServerAudioFile> audioFiles = null;

    /**
     * Brani non ancora riprodotti: la scelta del brano da eseguire viene
     * effettuata su questo vettore. Una volta scelto un brano, questo viene
     * rimosso fino a quando il vettore non risulta vuoto: a quel punto viene
     * ricopiato da {@link #audioFiles}
     */
    private Vector<ServerAudioFile> chosableAudioFiles = null;

    /**
     * Thread collegato a singola socket tcp di singolo client.
     *
     * @param sock Socket tcp già connessa al client
     * @throws IOException
     */
    public ServerClientHandler(final Socket sock, ServerPlayer playerServer)
            throws IOException {
        this.sock = sock;
        this.playerServer = playerServer;
        clientAddress = sock.getInetAddress();
        clientPort = sock.getPort();
        dis = new DataInputStream(sock.getInputStream());
        sock.setSoTimeout(SOCK_TIMEOUT * 1000);
        audioFiles = new Vector<>();
        chosableAudioFiles = new Vector<>();
    }

    /**
     * @return l'indirizzo del client come stringa nel formato
     * /xxx.xxx.xxx.xxx:xxxx
     */
    public final String getAddressAsString() {
        return clientAddress + ":" + clientPort;
    }

    /**
     * Chiede al client di iniziare lo streaming di un brano precedentemente
     * segnalato come disponibile.
     *
     * @return false se l'operazione è fallita, true altrimenti
     * @throws IOException
     */
    public final boolean startStreaming(final ServerAudioFile audioFile)
            throws IOException {
        PacketPlay p;

        if (isPlaying != null) {
            /*
             * Se risulta ancora in streaming un brano da parte di questo
			 * client, l'operazione fallirà
			 */
            return false;
        }
        System.err.println("[Server] richesto al client "
                + getAddressAsString() + " di mandare in streaming '"
                + audioFile.getMusicTitle() + "'");
        p = new PacketPlay(audioFile.getMusicId());
        isPlaying = audioFile;

        return p.toStream(new DataOutputStream(sock.getOutputStream()));
    }

    /**
     * Riceve la segnalazione del termine dello streaming di un file
     *
     * @throws Exception Se il file terminato non era quello in streaming
     */
    public final void streamingEnded(ServerAudioFile audioFile) throws Exception {
        if (isPlaying != audioFile) {
            throw new Exception("il file terminato non è quello che il client stava mandando in streaming");
        }
        isPlaying = null;
    }

    /**
     * Attende la terminazione del thread che sta eseguendo questa istanza e
     * rimuove i file audio del client dalla lista dei files disponibili per la
     * riproduzione. Durante l'attesa per la terminazione del thread, i files di
     * questo client verranno comunque ignorati da ServerPlayer perchè il
     * campo owner.alive viene impostato a false.
     *
     * @throws InterruptedException
     */
    public final void stop() throws InterruptedException {
        if (alive) {
            // Esegue join solamente quando invocato da un altro thread
            alive = false;
            if (runningThread != null && runningThread.isAlive()) {
                runningThread.join();
            }
        }
    }

    /**
     * Scelta del file audio da mandare in streaming. Il brano viene scelto fra
     * i brani {@link #chosableAudioFiles} che non sono ancora stati riprodotti.
     *
     * @return l'oggetto scelto
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public final ServerAudioFile pickAudioFile() throws IOException {
        ServerAudioFile res;
        int i;
        if (!alive || audioFiles.size() == 0) {
            return null;
        }
        if (chosableAudioFiles.size() == 0) {
            chosableAudioFiles = (Vector<ServerAudioFile>) audioFiles.clone();
        }
        i = (int) Math.round(Math.random() * (chosableAudioFiles.size() - 1));
        res = chosableAudioFiles.elementAt(i);
        chosableAudioFiles.remove(i);
        return res;
    }

    /**
     * @param runningThread il thread associato a questa istanza
     */
    public final void setRunningThread(Thread runningThread) {
        this.runningThread = runningThread;
    }

    /**
     * @return the runningThread
     */
    public final Thread getRunningThread() {
        return runningThread;
    }

    /**
     * @param alive stato del task
     */
    public final void setAlive(final boolean alive) {
        this.alive = alive;
    }

    /**
     * @return true se ancora attivo
     */
    public final boolean isAlive() {
        return alive;
    }

    /**
     * @return il socket associato a questo client
     */
    public final Socket getSocket() {
        return sock;
    }

    /**
     * Attende pacchetti di tipo MUSI, e li aggiunge alla lista dei file audio
     * condivisi mantenuta dal server principale.
     */
    @Override
    public final void run() {
        PacketMusic p;
        ServerAudioFile audioFile;
        System.err.println("[Server] Nuovo client: " + getAddressAsString());

        while (alive) {
            try {
                p = new PacketMusic();
                p.fromStream(dis);
                audioFile = new ServerAudioFile(p.musicId, p.musicTitle, this);
                audioFiles.add(audioFile);
                chosableAudioFiles.add(audioFile);
                System.out.println("[Server] il client " + getAddressAsString()
                        + " ha aggiunto file audio: '"
                        + audioFile.getMusicTitle() + "'");
                synchronized (playerServer) {
                    playerServer.notify();
                }
            } catch (SocketTimeoutException e) {
                // Timeout del socket, per verificare di essere ancora in
                // esecuzione
            } catch (SocketException e) {
                // Il client si è molto probabilmente disconnesso
                System.err.println("[Server] Il client ha perso la connessione viene chiuso:\n\t"
                        + e.getMessage());
                alive = false;
            } catch (UnknownPacketException e) {
                alive = false;
                System.err.println("[Server] Il client "
                        + getAddressAsString()
                        + " ha spedito un pacchetto non previsto e viene chiuso:\n\t"
                        + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                alive = false;
            }
        }
        try {
            stop();
        } catch (InterruptedException e) {
        }
    }

}
