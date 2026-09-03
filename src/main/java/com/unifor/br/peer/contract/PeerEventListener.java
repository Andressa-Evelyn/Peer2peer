package com.unifor.br.peer.contract;

/**
 * O que a rede avisa. Implementado pela Pessoa B no ChatController.
 *
 * <p><b>Atencao:</b> todos os metodos sao chamados em threads de rede. Qualquer atualizacao
 * de tela feita aqui precisa estar dentro de {@code Platform.runLater(...)}, senao o JavaFX
 * lanca {@code IllegalStateException} ou corrompe a lista silenciosamente.
 *
 * <p>As implementacoes nao devem bloquear: um callback lento segura a leitura daquele socket.
 */
public interface PeerEventListener {

    /** Chegou conversa: CHAT de qualquer peer, PRIVATE endereçada a este peer, ou o eco do proprio envio. */
    void onMessage(Message m);

    /** Um peer concluiu o handshake e entrou na malha. */
    void onPeerJoined(PeerInfo p);

    /** Um peer saiu, por LEAVE ou por queda de conexao detectada. */
    void onPeerLeft(PeerInfo p);

    /** Falha que o usuario precisa ver: porta ocupada, host inalcancavel, destinatario inexistente. */
    void onError(String motivo);
}
