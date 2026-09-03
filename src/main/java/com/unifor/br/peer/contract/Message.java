package com.unifor.br.peer.contract;

import java.util.UUID;

/**
 * Unidade que trafega no socket: uma linha JSON por mensagem.
 *
 * @param id         UUID unico, usado para descartar duplicata.
 * @param tipo       ver {@link MessageType}.
 * @param fromPeerId id de quem originou a mensagem.
 * @param fromUser   username de quem originou, para a interface nao precisar consultar a lista.
 * @param toPeerId   destinatario, preenchido apenas em PRIVATE.
 * @param texto      conteudo da conversa, ou payload JSON nas mensagens de controle.
 * @param timestamp  epoch millis de quando a mensagem foi criada na origem.
 */
public record Message(String id,
                      MessageType tipo,
                      String fromPeerId,
                      String fromUser,
                      String toPeerId,
                      String texto,
                      long timestamp) {

    public static Message chat(PeerInfo de, String texto) {
        return novo(MessageType.CHAT, de, null, texto);
    }

    public static Message privado(PeerInfo de, String paraPeerId, String texto) {
        return novo(MessageType.PRIVATE, de, paraPeerId, texto);
    }

    /** Mensagem de controle. O payload vai serializado em texto quando existir. */
    public static Message controle(MessageType tipo, PeerInfo de, String payload) {
        return novo(tipo, de, null, payload);
    }

    private static Message novo(MessageType tipo, PeerInfo de, String paraPeerId, String texto) {
        return new Message(UUID.randomUUID().toString(), tipo,
                de.peerId(), de.username(), paraPeerId, texto, System.currentTimeMillis());
    }

    /** True quando a mensagem so deve aparecer para um destinatario. */
    public boolean isPrivada() {
        return tipo == MessageType.PRIVATE;
    }

    /** True quando o conteudo e conversa, e nao controle de protocolo. */
    public boolean isConversa() {
        return tipo == MessageType.CHAT || tipo == MessageType.PRIVATE;
    }
}
