package com.unifor.br.peer.net;

import com.unifor.br.peer.contract.PeerInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camada de transporte: aceita conexoes, abre conexoes e mantem o mapa de quem esta vivo.
 *
 * <p>Nao entende protocolo. Recebe linha, entrega linha, avisa quando um socket morre. Toda a
 * decisao de o que fazer com o conteudo fica em {@link SocketPeerNetwork}.
 *
 */
public final class ConnectionManager {

    /** Callbacks para a camada de protocolo. Chamados sempre em thread de rede. */
    public interface ConnectionEvents {
        void onLine(PeerConnection origem, String linha);
        void onDisconnected(PeerConnection origem);
        void onTransportError(String motivo);
    }

    private static final int TIMEOUT_CONEXAO_MS = 5000;

    private final ConnectionEvents events;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Conexoes com handshake concluido, indexadas pelo peerId do outro lado. */
    private final Map<String, PeerConnection> estabelecidas = new ConcurrentHashMap<>();

    /** Conexoes abertas mas ainda sem identidade. Guardadas para o shutdown nao vazar socket. */
    private final Set<PeerConnection> pendentes = ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService readerPool;

    public ConnectionManager(ConnectionEvents events) {
        this.events = events;
    }

    /**
     * Abre o socket de escuta e comeca a aceitar conexoes.
     *
     * @param listenPort porta desejada, ou 0 para o sistema escolher uma livre.
     * @return a porta realmente vinculada.
     */
    public int start(int listenPort) throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("ConnectionManager ja esta rodando");
        }
        try {
            serverSocket = new ServerSocket(listenPort);
        } catch (IOException e) {
            running.set(false);
            throw e;
        }
        acceptExecutor = Executors.newSingleThreadExecutor(r -> thread(r, "peer-accept"));
        readerPool = Executors.newCachedThreadPool(r -> thread(r, "peer-reader"));
        acceptExecutor.submit(this::acceptLoop);
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                registrarSocket(socket, false);
            } catch (SocketException e) {
                // esperado quando o shutdown fecha o ServerSocket
                if (running.get()) {
                    events.onTransportError("Falha ao aceitar conexao: " + e.getMessage());
                }
                return;
            } catch (IOException e) {
                if (running.get()) {
                    events.onTransportError("Falha ao aceitar conexao: " + e.getMessage());
                }
            }
        }
    }

    /** Abre uma conexao de saida. A identidade so existe depois do handshake. */
    public PeerConnection dial(String host, int port) throws IOException {
        if (!running.get()) {
            throw new IllegalStateException("ConnectionManager nao esta rodando");
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_CONEXAO_MS);
        return registrarSocket(socket, true);
    }

    private PeerConnection registrarSocket(Socket socket, boolean iniciadaPorMim) throws IOException {
        socket.setTcpNoDelay(true);
        PeerConnection conexao = new PeerConnection(socket, iniciadaPorMim);
        pendentes.add(conexao);
        readerPool.submit(() -> readLoop(conexao));
        return conexao;
    }

    private void readLoop(PeerConnection conexao) {
        try {
            String linha;
            while ((linha = conexao.readLine()) != null) {
                try {
                    events.onLine(conexao, linha);
                } catch (RuntimeException e) {
                    // um bug no tratamento de UMA mensagem nao pode encerrar a leitura do socket
                    events.onTransportError("Falha ao processar mensagem: " + e);
                }
            }
        } catch (IOException e) {
            // socket caiu; o tratamento e o mesmo do fim de stream
        } finally {
            pendentes.remove(conexao);
            conexao.close();
            events.onDisconnected(conexao);
        }
    }

    /** Vincula a conexao a um peerId depois do handshake. Devolve a conexao que ocupava a chave, se havia. */
    public PeerConnection register(PeerConnection conexao, PeerInfo remoto) {
        conexao.bindRemote(remoto);
        pendentes.remove(conexao);
        return estabelecidas.put(remoto.peerId(), conexao);
    }

    public PeerConnection established(String peerId) {
        return estabelecidas.get(peerId);
    }

    public Collection<PeerConnection> all() {
        return new ArrayList<>(estabelecidas.values());
    }

    public List<PeerInfo> peersConectados() {
        List<PeerInfo> lista = new ArrayList<>();
        for (PeerConnection c : estabelecidas.values()) {
            PeerInfo r = c.remote();
            if (r != null) {
                lista.add(r);
            }
        }
        return lista;
    }

    /** Remove do mapa apenas se esta conexao for a atual daquele peerId. Evita remover a substituta. */
    public boolean removeIfCurrent(PeerConnection conexao) {
        PeerInfo remoto = conexao.remote();
        if (remoto == null) {
            return false;
        }
        return estabelecidas.remove(remoto.peerId(), conexao);
    }

    /** Fecha uma conexao por decisao nossa (duplicata ou shutdown), sem gerar aviso de saida de peer. */
    public void drop(PeerConnection conexao) {
        conexao.marcarDescartada();
        removeIfCurrent(conexao);
        pendentes.remove(conexao);
        conexao.close();
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Fecha tudo. Idempotente: chamar duas vezes nao lanca. */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (PeerConnection c : all()) {
            c.marcarDescartada();
            c.close();
        }
        for (PeerConnection c : pendentes) {
            c.marcarDescartada();
            c.close();
        }
        estabelecidas.clear();
        pendentes.clear();
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignorado) {
            // nada a fazer: ja estamos encerrando
        }
        encerrar(acceptExecutor);
        encerrar(readerPool);
    }

    private static void encerrar(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread thread(Runnable r, String nome) {
        Thread t = new Thread(r, nome);
        t.setDaemon(true);
        return t;
    }
}
