package com.unifor.br.peer.contract;

import java.util.Objects;

/**
 * Identidade de um peer na malha.
 *
 * @param peerId   UUID gerado na inicializacao. Estavel enquanto o processo viver.
 * @param username nome escolhido pelo usuario. Pode repetir entre peers, por isso nao serve como chave.
 * @param host     endereco pelo qual este peer e alcancavel. Preenchido por quem recebe a conexao.
 * @param port     porta em que este peer escuta novas conexoes (nao a porta efemera do socket).
 */
public record PeerInfo(String peerId, String username, String host, int port) {

    public PeerInfo {
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(username, "username");
    }

    /** Copia com o host preenchido a partir do endereco real do socket. */
    public PeerInfo withHost(String novoHost) {
        return new PeerInfo(peerId, username, novoHost, port);
    }

    /** Copia com a porta de escuta anunciada no handshake. */
    public PeerInfo withPort(int novaPorta) {
        return new PeerInfo(peerId, username, host, novaPorta);
    }

    /** Id abreviado, para exibir na interface quando dois peers usam o mesmo username. */
    public String shortId() {
        return peerId.substring(0, Math.min(8, peerId.length()));
    }

    /** Rotulo pronto para a lista de peers: "andressa (a1b2c3d4)". */
    public String display() {
        return username + " (" + shortId() + ")";
    }
}
