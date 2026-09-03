package com.unifor.br.peer.contract;

/**
 * Tipos de mensagem que trafegam no socket.
 *
 * <p>Mensagens de controle (HELLO, HELLO_ACK, PEER_LIST, LEAVE, PING) sao consumidas
 * pelo nucleo e nunca chegam a interface. Apenas CHAT e PRIVATE viram
 * {@link PeerEventListener#onMessage(Message)}.
 */
public enum MessageType {

    /** Apresentacao de quem abriu a conexao. O campo texto carrega o PeerInfo do remetente em JSON. */
    HELLO,

    /** Resposta da apresentacao. O campo texto carrega o PeerInfo de quem recebeu o HELLO. */
    HELLO_ACK,

    /** Lista dos peers que o remetente ja conhece. O campo texto carrega um array JSON de PeerInfo. */
    PEER_LIST,

    /** Mensagem de conversa para todos os peers da malha. */
    CHAT,

    /** Mensagem de conversa para um unico destinatario, indicado em toPeerId. */
    PRIVATE,

    /** Aviso de saida voluntaria, enviado antes de fechar a aplicacao. */
    LEAVE,

    /** Batimento periodico usado para detectar conexao morta. */
    PING
}
