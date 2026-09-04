package com.unifor.br.peer.net;

import com.unifor.br.peer.contract.PeerInfo;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Um socket TCP encapsulado.
 *
 */
public final class PeerConnection implements Closeable {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final boolean initiatedByMe;
    private final Object writeLock = new Object();
    private final AtomicBoolean discarded = new AtomicBoolean(false);

    private volatile PeerInfo remote;
    private volatile long lastSeen = System.currentTimeMillis();

    PeerConnection(Socket socket, boolean initiatedByMe) throws IOException {
        this.socket = socket;
        this.initiatedByMe = initiatedByMe;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /**
     * Escreve uma linha no socket.
     *
     * @return false se o socket ja morreu. Cabe ao chamador remover a conexao.
     */
    public boolean send(String linha) {
        synchronized (writeLock) {
            if (socket.isClosed()) {
                return false;
            }
            out.println(linha);
            return !out.checkError();
        }
    }

    String readLine() throws IOException {
        String linha = in.readLine();
        if (linha != null) {
            lastSeen = System.currentTimeMillis();
        }
        return linha;
    }

    /** Identidade do outro lado. Null enquanto o handshake nao concluiu. */
    public PeerInfo remote() {
        return remote;
    }

    void bindRemote(PeerInfo info) {
        this.remote = info;
    }

    public boolean isHandshakeCompleto() {
        return remote != null;
    }

    /** True quando este processo abriu a conexao; false quando ela chegou pelo ServerSocket. */
    public boolean initiatedByMe() {
        return initiatedByMe;
    }

    /** Endereco real do outro lado, usado para preencher o host do PeerInfo recebido no HELLO. */
    public String remoteHost() {
        return socket.getInetAddress().getHostAddress();
    }

    long lastSeen() {
        return lastSeen;
    }

    /**
     * Marca a conexao como fechada de proposito (duplicata descartada ou shutdown).
     * O laco de leitura consulta isso para nao anunciar saida de peer quando fomos nos que fechamos.
     */
    void marcarDescartada() {
        discarded.set(true);
    }

    boolean isDescartada() {
        return discarded.get();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignorado) {
            // fechar um socket ja morto nao e erro que interesse a ninguem
        }
    }

    @Override
    public String toString() {
        PeerInfo r = remote;
        return "PeerConnection[" + (r == null ? "handshake pendente" : r.display())
                + ", " + (initiatedByMe ? "saida" : "entrada") + "]";
    }
}
