/**
 */
package gd.choir.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Serve i dati come un input stream, mentre li riceve come pacchetti I
 * pacchetti già letti e non marcati (markPosition, markLimit), vengono rimossi
 * dal vettore dei pacchetti, permettendo al garbage collector di liberare la
 * memoria. I pacchetti vengono copiati in una coda di buffer di dimensione
 * parametrica.
 *
 * This class transform an incoming packet stream
 * @author Giulio D'Ambrosio
 */
public class AudioPacketStreamReader extends InputStream {
    private class PacketData {
        long startOffset;
        long endOffset;
        byte[] data;
        long dataSize;

        public PacketData(byte[] data, int dataSize, long startOffset) {
            if (data == null) {
                throw new NullPointerException();
            }

            this.data = data;
            this.dataSize = dataSize;
            this.startOffset = startOffset;
            this.endOffset = startOffset + dataSize;
        }
    }

    /**
     * Coda dei packets
     */
    private LinkedList<PacketData> packets;

    /**
     * Se true, lo stream è arrivato completamente
     */
    private boolean completed = false;

    private long streamPosition = 0L;

    private long streamAvailableSize = 0L;

    /**
     * Posizione memorizzata per rollback
     */
    private long markPosition = -1L;

    /**
     * Allontanamento dalla posizione marcata per rollback, da mantenere in
     * memoria
     */
    private long markLimit = 0L;

    public AudioPacketStreamReader() {
        packets = new LinkedList<>();
        this.streamAvailableSize = 0;
    }

    /**
     * Legge un singolo byte dallo stream
     *
     * @see java.io.InputStream#read()
     */
    @Override
    public synchronized int read() throws IOException {
        int res, off;
        PacketData p;

        if (streamPosition >= streamAvailableSize) {
            if (completed) {
                return -1;
            }
            // Stops execution waiting to be notified about an incoming packet
            try {
                wait();
            } catch (InterruptedException e) {
                // This happens when it is required to stop waiting
            }
            if (streamPosition >= streamAvailableSize) {
                // There are no more bytes to read: end of stream has been reached
                return completed ? -1 : 0;
            }
        }

        p = getPacket();
        off = (int) (streamPosition - p.startOffset);
        res = (int) p.data[off];
        res &= 0xff;
        streamPosition++;

        freePastBuffers();
        return res;
    }

    /**
     * Legge in un buffer fornito dall'utente, al più bufferLength bytes dallo
     * stream. Se non sono immediatamente disponibili, il metodo si blocca
     * (tramite wait) in attesa di un nuovo pacchetto.
     *
     * @see java.io.InputStream#read()
     */
    @Override
    public synchronized int read(byte[] buffer, int bufferOffset,
                                 int bufferLength) throws IOException {
        PacketData p;

        long off, len;

        if (buffer == null) {
            throw new NullPointerException();
        }

        if (bufferOffset < 0 || bufferLength < 0
                || (bufferLength > buffer.length - bufferOffset)) {
            throw new IndexOutOfBoundsException();
        }

        if (bufferLength == 0) {
            return 0;
        }

        if (streamPosition >= streamAvailableSize) {
            while (!completed && streamPosition >= streamAvailableSize) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // richiesta l'interruzione della lettura
                }
            }
            if (completed && streamPosition >= streamAvailableSize) {
                // Stream terminato
                return -1;
            }
        }
        if (streamPosition + bufferLength > streamAvailableSize) {
            // Se l'streamPosition e/o la lunghezza dei dati richiesti superano i dati
            // disponibili,
            /*
			 * altrimenti si blocca in attesa del prossimo pacchetto dati
			 */
            while (!completed && streamPosition + bufferLength > streamAvailableSize) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // richiesta l'interruzione della lettura
                }
            }
            if (streamPosition + bufferLength > streamAvailableSize) {
                bufferLength -= (streamPosition + bufferLength) - streamAvailableSize;
            }
        }

        if (bufferLength == 0) {
            return completed ? -1 : 0;
        }

        // Legge i dati richiesti da tutti i pacchetti necessari
        long curbuflen = bufferLength, curbufoff = bufferOffset;
        ListIterator<PacketData> i;
        i = packets.listIterator();
        while (true) {
            if (!i.hasNext()) {
                throw new IOException("out of packets error");
            }
            p = i.next();
            if (streamPosition >= p.startOffset && streamPosition < p.endOffset) {
                break;
            }
        }
        while (curbuflen > 0) {
            off = (int) (streamPosition - p.startOffset);
            len = Math.min(p.dataSize - off, curbuflen);
            System.arraycopy(p.data, (int) off, buffer, (int) curbufoff, (int) len);

            streamPosition += len;
            curbufoff += len;
            curbuflen -= len;
            if (!i.hasNext()) {
                break;
            } else {
                p = i.next();
            }
        }
        // Libera eventualmente i pacchetti che non verranno più richiesti
        freePastBuffers();
        return (int) (bufferLength - curbuflen);
    }

    /**
     * Libera tutti i pacchetti che non possono essere più richiesti in quanto
     * letti, ed oltre l'eventuale limite precedentemente marcato tramite
     * chiamata al metodo mark.
     * Frees all the packets that can't be reached anymore because already read
     * and behind the optional limit previosuly set by calling {@link gd.choir.client.AudioPacketStreamReader#mark}
     */
    private synchronized void freePastBuffers() {
        if (markPosition < 0
                || (markPosition >= 0 && markPosition + markLimit < streamPosition)) {
            // libera la memoria per i packet già letti e non marcati
            while (packets.size() > 1
                    && packets.getFirst().endOffset - 1 < streamPosition) {
                packets.removeFirst();
            }
        }
    }

    /**
     * Restituisce il buffer che contiene il byte all'streamPosition corrente.
     *
     * @return il buffer corrente
     */
    private synchronized PacketData getPacket() {
        if (streamPosition < 0 || streamPosition >= streamAvailableSize || packets.size() == 0) {
            throw new IndexOutOfBoundsException();
        }
        PacketData res = null;
        for (PacketData packet : packets) {
            res = packet;
            if (streamPosition >= res.startOffset && streamPosition < res.endOffset) {
                break;
            }
        }
        return res;
    }

    /**
     * Aggiunge al buffer in coda un nuovo pacchetto di dati
     *
     * @param packetData Buffer dei dati da aggiungere
     * @param packetSize Numero di bytes da copiare
     */
    public synchronized void addPacket(byte[] packetData, int packetSize)
            throws IOException {
        if (this.completed) {
            throw new IOException("file chiuso: impossibile aggiungere un nuovo pacchetto");
        }
        if (packetData == null || packetSize <= 0) {
            throw new IOException("null packet data");
        }
        packets.addLast(new PacketData(packetData, packetSize, streamAvailableSize));
        streamAvailableSize += packetSize;
        notify();
    }

    /**
     * Restituisce il numero di bytes disponibili prima che una read causi un
     * blocco in attesa di dati non ancora arrivati.
     */
    public synchronized int available() throws IOException {
        return (int) (streamAvailableSize - streamPosition);
    }

    /**
     * Chiude lo stream, provocando anche lo sblocco dell'eventuale wait
     * dell'oggetto durante una read bloccante.
     */
    public synchronized void close() throws IOException {
        streamAvailableSize = 0;
        streamPosition = 0;
        setCompleted();
    }

    /**
     * Memorizza la posizione attuale: alla successiva chiamata del metodo
     * reset, verrà ripristinata la posizione in cui si trovava streamPosition al
     * momento di questa chiamata. Se durante la lettura saranno stati letti
     * markLimit bytes, senza che la posizione sia stata resettata, il limite
     * andrà perso, così come i dati precedenti all'streamPosition corrente.
     *
     * @param markLimit Numero di bytes oltrepassati i quali, la posizione marcata
     *                  sarà persa
     */
    public synchronized void mark(int markLimit) {
        markPosition = streamPosition;
        this.markLimit = markLimit;
    }

    /**
     */
    public synchronized void reset() throws IOException {
        if (markPosition < 0) {
            throw new IOException("Resetting to invalid mark");
        }
        streamPosition = markPosition;
    }

    /**
     * Indica che questo stream supporta reset e mark.
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * Marca questo stream come completo: sono disponibili senza rischio di
     * blocchi tutti i bytes fra la posizione attuale e la fine del file. Se si
     * tentasse di aggiungere un pacchetto dopo la chiamata a questo metodo,
     * addPacket lancerebbe un'eccezione.
     */
    public synchronized void setCompleted() {
        completed = true;
        notify();
    }

    /**
     * Controlla se lo stream è completo (@see setComplete)
     */
    public synchronized boolean isCompleted() {
        return completed;
    }

}
