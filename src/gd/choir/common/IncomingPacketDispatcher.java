/**
 *
 */
package gd.choir.common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentLinkedQueue;

import gd.choir.proto.packets.*;
import gd.choir.proto.packets.PacketJoin;
import gd.choir.proto.packets.Packet;
import gd.choir.proto.packets.audio.PacketBegin;
import gd.choir.proto.packets.audio.PacketData;
import gd.choir.proto.packets.audio.PacketEnd;

/**
 * Gestore del gruppo multicast.
 *
 * @author Giulio D'Ambrosio
 */
public class IncomingPacketDispatcher implements Runnable {

    /**
     * Intervallo di tempo massimo entro cui il thread verificherà
     * di essere ancora in esecuzione.
     * Ovvero che {@link #alive} sia true.
     */
    private static final int ALIVE_CHECK_TIMEOUT = 10 * 1000;

    /**
     * Socket multicast con cui comunica questa istanza.
     */
    MulticastSocket mso;

    /**
     * Indirizzo del gruppo multicast connesso a questa istanza
     */
    InetAddress groupAddress;

    /**
     * Porta del gruppo multicast connesso a questa istanza
     */
    char groupPort;

    /**
     * Flag: se false il thread è chiuso o in chiusura
     */
    boolean alive = true;

    /**
     * Thread in esecuzione associato a questa istanza
     */
    private Thread runningThread;

    /**
     * Istanze di classi interessate a pacchetti audio di tipo BEGI.
     */
    ConcurrentLinkedQueue<AudioBeginPacketListener> audioBeginListeners;

    /**
     * Istanze di classi interessate a pacchetti audio di tipo DATA.
     */
    ConcurrentLinkedQueue<AudioDataPacketListener> audioDataListeners;

    /**
     * Istanze di classi interessate a pacchetti audio di tipo ENDF.
     */
    ConcurrentLinkedQueue<AudioEndPacketListener> audioEndListeners;

    /**
     * Istanze di classi interessate a pacchetti HELO.
     */
    ConcurrentLinkedQueue<HelloPacketListener> helloListeners;

    /**
     * Istanze di classi interessate a pacchetti JOIN.
     */
    ConcurrentLinkedQueue<JoinPacketListener> joinListeners;

    /**
     * Costruttore.
     *
     * @throws IOException
     */
    public IncomingPacketDispatcher(InetAddress groupAddress,
                                    char groupPort) throws IOException {
        this(groupAddress, groupPort, null);
    }

    /**
     * Costruttore.
     *
     * @throws IOException
     */
    public IncomingPacketDispatcher(InetAddress groupAddress,
                                    char groupPort, MulticastSocket mso) throws IOException {
        super();
        this.groupAddress = groupAddress;
        this.groupPort = groupPort;
        if (mso == null) {
            mso = new MulticastSocket(groupPort);
            mso.joinGroup(groupAddress);
            // mso.setLoopbackMode(true);
        }
        mso.setSoTimeout(ALIVE_CHECK_TIMEOUT);
        this.mso = mso;

        audioBeginListeners = new ConcurrentLinkedQueue<>();
        audioDataListeners = new ConcurrentLinkedQueue<>();
        audioEndListeners = new ConcurrentLinkedQueue<>();
        helloListeners = new ConcurrentLinkedQueue<>();
        joinListeners = new ConcurrentLinkedQueue<>();
    }

    /**
     * In chiusura viene dissociato il socket dal gruppo multicast.
     */
    protected void finalize() throws Throwable {
        super.finalize();
        if (mso != null) {
            mso.leaveGroup(groupAddress);
        }
    }

    /**
     * Memorizza un'istanza come interessata a pacchetti audio di tipo
     * begin.
     *
     * @param pl Istanza di un oggetto che riceverà i pacchetti begin
     */
    public void registerListener(AudioBeginPacketListener pl) {
        synchronized (audioBeginListeners) {
            audioBeginListeners.add(pl);
        }
    }

    /**
     * Rimuove un'istanza come interessata a pacchetti audio di tipo
     * begin.
     *
     * @param pl Istanza di un oggetto che non riceverà i pacchetti begin
     */
    public void unregisterListener(AudioBeginPacketListener pl) {
        synchronized (audioBeginListeners) {
            audioBeginListeners.remove(pl);
        }
    }

    /**
     * Memorizza un'istanza come interessata a pacchetti audio di tipo
     * data.
     *
     * @param pl Istanza di un oggetto che riceverà i pacchetti data
     */
    public void registerListener(AudioDataPacketListener pl) {
        synchronized (audioDataListeners) {
            audioDataListeners.add(pl);
        }
    }

    /**
     * Rimuove un'istanza come interessata a pacchetti audio di tipo
     * data.
     *
     * @param pl Istanza di un oggetto che non riceverà i pacchetti data
     */
    public void unregisterListener(AudioDataPacketListener pl) {
        synchronized (audioDataListeners) {
            audioDataListeners.remove(pl);
        }
    }

    /**
     * Memorizza un'istanza come interessata a pacchetti audio di tipo
     * endf.
     *
     * @param pl Istanza di un oggetto che riceverà i pacchetti endf
     */
    public void registerListener(AudioEndPacketListener pl) {
        synchronized (audioEndListeners) {
            audioEndListeners.add(pl);
        }
    }

    /**
     * Rimuove un'istanza come interessata a pacchetti audio di tipo
     * endf.
     *
     * @param pl Istanza di un oggetto che non riceverà i pacchetti endf
     */
    public void unregisterListener(AudioEndPacketListener pl) {
        synchronized (audioEndListeners) {
            audioEndListeners.remove(pl);
        }
    }

    /**
     * Memorizza un'istanza come interessata a pacchetti di tipo
     * join.
     *
     * @param pl Istanza di un oggetto che riceverà i pacchetti join
     */
    public void registerListener(JoinPacketListener pl) {
        synchronized (joinListeners) {
            joinListeners.add(pl);
        }
    }

    /**
     * Rimuove un'istanza come interessata a pacchetti di tipo
     * join.
     *
     * @param pl Istanza di un oggetto che non riceverà i pacchetti join
     */
    public void unregisterListener(JoinPacketListener pl) {
        synchronized (joinListeners) {
            joinListeners.remove(pl);
        }
    }

    /**
     * Memorizza un'istanza come interessata a pacchetti di tipo
     * hello.
     *
     * @param pl Istanza di un oggetto che riceverà i pacchetti hello
     */
    public void registerListener(HelloPacketListener pl) {
        synchronized (helloListeners) {
            helloListeners.add(pl);
        }
    }

    /**
     * Rimuove un'istanza come interessata a pacchetti di tipo
     * hello.
     *
     * @param pl Istanza di un oggetto che non riceverà i pacchetti hello
     */
    public void unregisterListener(HelloPacketListener pl) {
        synchronized (helloListeners) {
            helloListeners.remove(pl);
        }
    }

    /**
     * Ascolta il socket multicast e notifica tutti gli oggetti registrati
     * dell'arrivo di un pacchetto del tipo richiesto.
     */
    @Override
    public void run() {
        byte[] buf = new byte[Packet.MAX_SIZE];
        java.net.DatagramPacket dp = new java.net.DatagramPacket(buf, buf.length);
        DatagramPacket jbdp;

        while (alive) {
            try {
                mso.receive(dp);
                jbdp = DatagramPacket.fromDatagram(dp);
                if (jbdp instanceof PacketData) {
                    synchronized (audioDataListeners) {
                        for (AudioDataPacketListener l : audioDataListeners) {
                            l.packetArrived((PacketData) jbdp);
                        }
                    }
                } else if (jbdp instanceof PacketBegin) {
                    synchronized (audioBeginListeners) {
                        for (AudioBeginPacketListener l : audioBeginListeners) {
                            l.packetArrived((PacketBegin) jbdp);
                        }
                    }
                } else if (jbdp instanceof PacketEnd) {
                    synchronized (audioEndListeners) {
                        for (AudioEndPacketListener l : audioEndListeners) {
                            l.packetArrived((PacketEnd) jbdp);
                        }
                    }
                } else if (jbdp instanceof PacketJoin) {
                    synchronized (joinListeners) {
                        for (JoinPacketListener l : joinListeners) {
                            l.packetArrived((PacketJoin) jbdp);
                        }
                    }
                } else if (jbdp instanceof PacketHello) {
                    synchronized (helloListeners) {
                        for (HelloPacketListener l : helloListeners) {
                            l.packetArrived((PacketHello) jbdp);
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                e.printStackTrace();
                alive = false;
            }
        }
    }

    /**
     * Spedisce un pacchetto verso il gruppo multicast
     *
     * @param p Il pacchetto da spedire
     * @throws IOException
     */
    public synchronized void send(DatagramPacket p) throws IOException {
        mso.send(p.getRawPacket());
    }

    /**
     * Lancia il thread associato a questa istanza.
     */
    public void start() {
        getRunningThread().start();
    }

    /**
     * Attende il termine dell'esecuzione del thread associato a questa istanza.
     *
     * @throws InterruptedException
     */
    public void stop() throws InterruptedException {
        getRunningThread().join();
    }

    /**
     * Interrompe il thread associato a questa istanza entro {@link #ALIVE_CHECK_TIMEOUT}
     * secondi.
     * Il metodo ritorna quando il thread è sicuramente terminato.
     *
     * @throws InterruptedException
     */
    public void stopNow() throws InterruptedException {
        alive = false;
        stop();
    }

    /**
     * @param runningThread the runningThread to set
     */
    public void setRunningThread(Thread runningThread) {
        this.runningThread = runningThread;
    }

    /**
     * @return the runningThread
     */
    public Thread getRunningThread() {
        return runningThread;
    }
}
