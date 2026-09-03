package com.unifor.br.peer.testes;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.PeerEventListener;
import com.unifor.br.peer.contract.PeerInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Listener de teste: guarda tudo que a rede avisou.
 *
 * <p>Faz o papel que o ChatController da Pessoa B fara na interface — e serve de exemplo de
 * que o nucleo chama estes metodos em threads de rede, por isso as listas sao sincronizadas.
 */
final class Coletor implements PeerEventListener {

    private final String nome;
    final List<Message> mensagens = Collections.synchronizedList(new ArrayList<>());
    final List<PeerInfo> entradas = Collections.synchronizedList(new ArrayList<>());
    final List<PeerInfo> saidas = Collections.synchronizedList(new ArrayList<>());
    final List<String> erros = Collections.synchronizedList(new ArrayList<>());

    Coletor(String nome) {
        this.nome = nome;
    }

    @Override
    public void onMessage(Message m) {
        mensagens.add(m);
    }

    @Override
    public void onPeerJoined(PeerInfo p) {
        entradas.add(p);
    }

    @Override
    public void onPeerLeft(PeerInfo p) {
        saidas.add(p);
    }

    @Override
    public void onError(String motivo) {
        erros.add(motivo);
    }

    /** Textos recebidos, na ordem. */
    List<String> textos() {
        synchronized (mensagens) {
            List<String> textos = new ArrayList<>();
            for (Message m : mensagens) {
                textos.add(m.texto());
            }
            return textos;
        }
    }

    long quantasVezesRecebeu(String texto) {
        return textos().stream().filter(texto::equals).count();
    }

    boolean saiuPeer(String peerId) {
        synchronized (saidas) {
            return saidas.stream().anyMatch(p -> p.peerId().equals(peerId));
        }
    }

    @Override
    public String toString() {
        return "Coletor[" + nome + "]";
    }
}
