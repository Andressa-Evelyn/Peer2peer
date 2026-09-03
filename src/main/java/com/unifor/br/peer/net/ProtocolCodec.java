package com.unifor.br.peer.net;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.MessageType;
import com.unifor.br.peer.contract.PeerInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduz entre objetos e as linhas JSON que trafegam no socket (tarefa A4).
 *
 * <p>Uma mensagem = uma linha. O escritor nunca gera quebra de linha crua dentro do JSON
 * (um Enter digitado pelo usuario vira {@code \\n} escapado), entao
 * {@code BufferedReader.readLine()} sempre devolve exatamente uma mensagem inteira.
 *
 * <p>Linha malformada gera {@link IOException}, e o chamador apenas descarta aquela linha.
 * Um peer com bug de serializacao nao pode derrubar a conexao dos outros.
 */
public final class ProtocolCodec {

    // ------------------------------------------------------------------ Message

    public String encode(Message m) throws IOException {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("id", m.id());
        campos.put("tipo", m.tipo().name());
        campos.put("fromPeerId", m.fromPeerId());
        campos.put("fromUser", m.fromUser());
        campos.put("toPeerId", m.toPeerId());
        campos.put("texto", m.texto());
        campos.put("timestamp", m.timestamp());
        return Json.escrever(campos);
    }

    public Message decode(String linha) throws IOException {
        Map<String, Object> campos = Json.lerObjeto(linha);
        String id = texto(campos, "id");
        String tipoBruto = texto(campos, "tipo");
        String fromPeerId = texto(campos, "fromPeerId");
        if (id == null || tipoBruto == null || fromPeerId == null) {
            throw new IOException("mensagem sem campos obrigatorios: " + linha);
        }
        MessageType tipo;
        try {
            tipo = MessageType.valueOf(tipoBruto);
        } catch (IllegalArgumentException e) {
            // peer rodando uma versao mais nova do protocolo
            throw new IOException("tipo de mensagem desconhecido: " + tipoBruto);
        }
        return new Message(id, tipo, fromPeerId,
                texto(campos, "fromUser"),
                texto(campos, "toPeerId"),
                texto(campos, "texto"),
                numero(campos, "timestamp"));
    }

    // ----------------------------------------------------------------- PeerInfo

    public String encodePeer(PeerInfo p) throws IOException {
        return Json.escrever(mapaDe(p));
    }

    public PeerInfo decodePeer(String json) throws IOException {
        return peerDe(Json.lerObjeto(json));
    }

    public String encodePeerList(Collection<PeerInfo> peers) throws IOException {
        List<Object> itens = new ArrayList<>();
        for (PeerInfo p : peers) {
            itens.add(mapaDe(p));
        }
        return Json.escrever(itens);
    }

    @SuppressWarnings("unchecked")
    public List<PeerInfo> decodePeerList(String json) throws IOException {
        List<PeerInfo> peers = new ArrayList<>();
        for (Object item : Json.lerLista(json)) {
            if (!(item instanceof Map)) {
                throw new IOException("item invalido na lista de peers");
            }
            peers.add(peerDe((Map<String, Object>) item));
        }
        return peers;
    }

    // --------------------------------------------------------------- auxiliares

    private static Map<String, Object> mapaDe(PeerInfo p) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("peerId", p.peerId());
        campos.put("username", p.username());
        campos.put("host", p.host());
        campos.put("port", p.port());
        return campos;
    }

    private static PeerInfo peerDe(Map<String, Object> campos) throws IOException {
        String peerId = texto(campos, "peerId");
        String username = texto(campos, "username");
        if (peerId == null || username == null) {
            throw new IOException("peer sem peerId ou username");
        }
        return new PeerInfo(peerId, username, texto(campos, "host"), (int) numero(campos, "port"));
    }

    private static String texto(Map<String, Object> campos, String chave) throws IOException {
        Object valor = campos.get(chave);
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof String s)) {
            throw new IOException("campo " + chave + " deveria ser texto");
        }
        return s;
    }

    private static long numero(Map<String, Object> campos, String chave) throws IOException {
        Object valor = campos.get(chave);
        if (valor == null) {
            return 0L;
        }
        if (!(valor instanceof Number n)) {
            throw new IOException("campo " + chave + " deveria ser numero");
        }
        return n.longValue();
    }
}
