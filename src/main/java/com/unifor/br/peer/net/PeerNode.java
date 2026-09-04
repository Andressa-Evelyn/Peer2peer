package com.unifor.br.peer.net;

import com.unifor.br.peer.contract.PeerInfo;

import java.util.UUID;

/**
 * Identidade deste peer.
 *
 * <p>Substitui o antigo par "username solto + porta": agora existe um {@code peerId} estavel,
 * gerado uma vez na inicializacao, que e a chave de tudo — mapa de conexoes, destinatario de
 * mensagem privada e desempate de conexao duplicada. O username e so rotulo de tela e pode
 * repetir entre peers.
 *
 * <p>O host fica sempre nulo na identidade local: quem descobre o endereco alcancavel deste peer
 * e o outro lado, lendo o endereco remoto do socket. Publicar aqui um IP adivinhado
 * (127.0.0.1, ou a primeira interface da maquina) e a origem classica de "funciona local,
 * falha entre maquinas".
 */
public final class PeerNode {

    private final String peerId = UUID.randomUUID().toString();
    private final String username;
    private volatile int listenPort;

    public PeerNode(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username obrigatorio");
        }
        this.username = username.trim();
    }

    /** Chamado pelo ConnectionManager depois do bind, quando a porta 0 vira uma porta real. */
    void definirPortaDeEscuta(int porta) {
        this.listenPort = porta;
    }

    public String peerId() {
        return peerId;
    }

    public String username() {
        return username;
    }

    public int listenPort() {
        return listenPort;
    }

    public PeerInfo info() {
        return new PeerInfo(peerId, username, null, listenPort);
    }
}
