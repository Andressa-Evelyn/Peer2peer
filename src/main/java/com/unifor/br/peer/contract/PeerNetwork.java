package com.unifor.br.peer.contract;

import java.util.List;

/**
 * O que a interface chama. Implementado pela Pessoa A em
 * {@code com.unifor.br.peer.net.SocketPeerNetwork}.
 *
 * <p>Nenhum metodo aqui bloqueia esperando resposta da rede: {@code connectTo} apenas
 * dispara o handshake, e a confirmacao chega depois por
 * {@link PeerEventListener#onPeerJoined(PeerInfo)}.
 *
 * <p>Pessoa B: implemente um dublê desta interface (FakePeerNetwork, tarefa B6) para
 * construir a tela sem depender do nucleo.
 */
public interface PeerNetwork {

    /**
     * Define quem recebe os eventos da rede. Precisa ser chamado antes de {@link #start}.
     * Os callbacks chegam em thread de rede, nunca na thread do JavaFX.
     */
    void setListener(PeerEventListener listener);

    /**
     * Sobe o socket de escuta e passa a aceitar conexoes.
     *
     * @param listenPort porta de escuta. Use 0 para deixar o sistema escolher uma livre
     *                   e leia a porta real em {@link #self()}.
     * @throws IllegalStateException se ja estiver rodando ou se a porta estiver ocupada.
     */
    void start(String username, int listenPort);

    /** Entra em uma malha existente pelo endereco de qualquer participante dela. */
    void connectTo(String host, int port);

    /** Envia uma mensagem para todos os peers conectados. */
    void broadcast(String texto);

    /** Envia uma mensagem que so o destinatario recebe. */
    void sendPrivate(String peerId, String texto);

    /** Peers com handshake concluido neste momento. Copia defensiva, pode ser lida a qualquer momento. */
    List<PeerInfo> connectedPeers();

    /** Identidade deste peer, com a porta de escuta ja resolvida. Null antes de {@link #start}. */
    PeerInfo self();

    /** Avisa a malha, fecha os sockets e encerra as threads. Idempotente. */
    void shutdown();
}
