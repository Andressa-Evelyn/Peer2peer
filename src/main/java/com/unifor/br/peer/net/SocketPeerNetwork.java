package com.unifor.br.peer.net;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.MessageType;
import com.unifor.br.peer.contract.PeerEventListener;
import com.unifor.br.peer.contract.PeerInfo;
import com.unifor.br.peer.contract.PeerNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementacao do contrato sobre sockets TCP (tarefas A4 a A7).
 *
 * <p>Topologia em malha completa: todo peer mantem uma conexao direta com todo outro peer.
 * O broadcast nao precisa de repasse e a mensagem privada vai pela conexao do destinatario,
 * sem passar por terceiros.
 *
 * <h2>Handshake</h2>
 * <pre>
 *   novo -> existente : HELLO      (identidade do novo, com a porta de escuta)
 *   existente -> novo : HELLO_ACK  (identidade do existente)
 *   existente -> novo : PEER_LIST  (os outros peers que o existente conhece)
 *   novo -> cada um da lista : HELLO ...
 * </pre>
 *
 * <p>O host anunciado no HELLO e ignorado de proposito: quem recebe preenche com o endereco
 * real do socket. So a porta de escuta vem do payload, porque a porta do socket de entrada
 * e efemera e nao serve para reconectar.
 */
public final class SocketPeerNetwork implements PeerNetwork, ConnectionManager.ConnectionEvents {

    private static final long PING_INTERVALO_MS = 5_000;
    private static final long LIMITE_SEM_NOTICIAS_MS = 20_000;
    private static final int MAX_IDS_LEMBRADOS = 500;

    /**
     * Janela de confirmacao antes de anunciar que um peer saiu.
     *
     * <p>Quando duas conexoes duplicadas sao resolvidas, a perdedora fecha — e o outro lado ve
     * um socket morrendo para um peer que continua ali pela conexao vencedora. Sem essa espera,
     * a lista da interface piscaria "fulano saiu / fulano entrou" a cada conexao simultanea.
     */
    private static final long GRACA_SAIDA_MS = 300;

    private final ProtocolCodec codec = new ProtocolCodec();
    private final ConnectionManager manager = new ConnectionManager(this);

    /** Serializa as decisoes de handshake: sem isso, duas conexoes simultaneas se registram por cima. */
    private final Object meshLock = new Object();

    /**
     * peerIds com dial em andamento (valor = instante em que comecou), para a PEER_LIST nao abrir
     * dois sockets para o mesmo peer. Entradas presas sao expiradas na rotina de manutencao,
     * senao um handshake que nunca conclui bloquearia a reconexao com aquele peer para sempre.
     */
    private final Map<String, Long> dialing = new ConcurrentHashMap<>();

    /** Ids de mensagem ja entregues, para descartar duplicata (A6). */
    private final Map<String, Boolean> idsVistos = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_IDS_LEMBRADOS;
                }
            });

    /**
     * peerIds ja anunciados como presentes. Torna os avisos idempotentes: a interface recebe
     * uma entrada e uma saida por peer, independentemente de quantos sockets foram abertos,
     * trocados ou descartados por baixo.
     */
    private final Set<String> anunciados = ConcurrentHashMap.newKeySet();

    private volatile PeerEventListener listener = new ListenerSilencioso();
    private volatile PeerNode node;
    private volatile boolean rodando;

    private ExecutorService dialExecutor;
    private ScheduledExecutorService manutencao;

    // ------------------------------------------------------------------ contrato

    @Override
    public void setListener(PeerEventListener listener) {
        this.listener = listener == null ? new ListenerSilencioso() : listener;
    }

    @Override
    public void start(String username, int listenPort) {
        if (rodando) {
            throw new IllegalStateException("este peer ja foi iniciado");
        }
        PeerNode novo = new PeerNode(username);
        int portaReal;
        try {
            portaReal = manager.start(listenPort);
        } catch (IOException e) {
            throw new IllegalStateException("nao foi possivel escutar na porta " + listenPort
                    + ": " + e.getMessage(), e);
        }
        novo.definirPortaDeEscuta(portaReal);
        this.node = novo;
        this.rodando = true;

        dialExecutor = Executors.newCachedThreadPool(r -> daemon(r, "peer-dial"));
        manutencao = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "peer-manutencao"));
        manutencao.scheduleAtFixedRate(this::rotinaDeManutencao,
                PING_INTERVALO_MS, PING_INTERVALO_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void connectTo(String host, int port) {
        exigirRodando();
        // Nao bloqueia quem chamou: o dial tem timeout de 5s e travaria a thread do JavaFX.
        dialExecutor.submit(() -> {
            try {
                PeerConnection conexao = manager.dial(host, port);
                enviar(conexao, Message.controle(MessageType.HELLO, node.info(), payloadIdentidade()));
            } catch (IOException e) {
                avisarErro("Nao foi possivel conectar em " + host + ":" + port + " (" + e.getMessage() + ")");
            }
        });
    }

    @Override
    public void broadcast(String texto) {
        exigirRodando();
        if (texto == null || texto.isBlank()) {
            return;
        }
        Message m = Message.chat(node.info(), texto);
        idsVistos.put(m.id(), Boolean.TRUE);
        for (PeerConnection c : manager.all()) {
            enviar(c, m);
        }
        entregar(m); // eco local: quem enviou tambem ve a propria mensagem na tela
    }

    @Override
    public void sendPrivate(String peerId, String texto) {
        exigirRodando();
        if (texto == null || texto.isBlank()) {
            return;
        }
        PeerConnection destino = manager.established(peerId);
        if (destino == null) {
            avisarErro("Peer nao esta mais conectado; a mensagem privada nao foi enviada.");
            return;
        }
        Message m = Message.privado(node.info(), peerId, texto);
        idsVistos.put(m.id(), Boolean.TRUE);
        if (!enviar(destino, m)) {
            avisarErro("Falha ao enviar a mensagem privada para " + destino.remote().display() + ".");
            return;
        }
        entregar(m); // eco local, para a conversa privada mostrar o que foi enviado
    }

    @Override
    public List<PeerInfo> connectedPeers() {
        return manager.peersConectados();
    }

    @Override
    public PeerInfo self() {
        PeerNode atual = node;
        return atual == null ? null : atual.info();
    }

    @Override
    public void shutdown() {
        if (!rodando) {
            return;
        }
        rodando = false;
        // Avisa a malha antes de fechar, para os outros removerem este peer na hora
        // em vez de esperarem o timeout do PING.
        Message adeus = Message.controle(MessageType.LEAVE, node.info(), null);
        for (PeerConnection c : manager.all()) {
            enviar(c, adeus);
        }
        dormir(120); // janela curta para o LEAVE sair do buffer antes do close
        encerrar(dialExecutor);
        if (manutencao != null) {
            manutencao.shutdownNow();
        }
        manager.shutdown();
    }

    // ------------------------------------------------------- eventos de transporte

    @Override
    public void onLine(PeerConnection origem, String linha) {
        Message m;
        try {
            m = codec.decode(linha);
        } catch (IOException e) {
            // linha corrompida ou de um peer com versao diferente: descarta e segue lendo
            avisarErro("Mensagem ignorada por estar malformada.");
            return;
        }
        switch (m.tipo()) {
            case HELLO -> tratarHello(origem, m, true);
            case HELLO_ACK -> tratarHello(origem, m, false);
            case PEER_LIST -> tratarPeerList(m);
            case CHAT, PRIVATE -> tratarConversa(m);
            case LEAVE -> removerEAnunciar(origem);
            case PING -> { /* lastSeen ja foi atualizado na leitura */ }
        }
    }

    @Override
    public void onDisconnected(PeerConnection origem) {
        if (origem.isDescartada()) {
            return; // fomos nos que fechamos: duplicata descartada ou shutdown
        }
        removerEAnunciar(origem);
    }

    @Override
    public void onTransportError(String motivo) {
        avisarErro(motivo);
    }

    // ------------------------------------------------------------------ handshake

    private void tratarHello(PeerConnection conexao, Message m, boolean responder) {
        PeerInfo anunciado;
        try {
            anunciado = codec.decodePeer(m.texto());
        } catch (IOException e) {
            avisarErro("Handshake invalido recebido; conexao descartada.");
            manager.drop(conexao);
            return;
        }

        // O host confiavel e o endereco real do socket, nunca o que o outro lado afirmou.
        PeerInfo remoto = anunciado.withHost(conexao.remoteHost());

        synchronized (meshLock) {
            if (remoto.peerId().equals(node.peerId())) {
                manager.drop(conexao); // auto-conexao: alguem passou o proprio endereco
                return;
            }
            PeerConnection existente = manager.established(remoto.peerId());
            if (existente != null && existente != conexao) {
                if (!vencemos(conexao, existente, remoto.peerId())) {
                    manager.drop(conexao); // duplicata perdedora; a malha ja tem esse peer
                    return;
                }
                manager.drop(existente); // trocamos o socket, mas o peer continua o mesmo
            }
            manager.register(conexao, remoto);
            dialing.remove(remoto.peerId());
        }

        if (responder) {
            enviar(conexao, Message.controle(MessageType.HELLO_ACK, node.info(), payloadIdentidade()));
            enviarPeerList(conexao, remoto.peerId());
        }
        if (anunciados.add(remoto.peerId())) {
            notificarEntrada(remoto);
        }
    }

    /**
     * Desempate de conexao duplicada: A conecta em B no mesmo instante em que B conecta em A.
     *
     * <p>Sobrevive a conexao iniciada pelo peer de menor peerId. Como os dois lados comparam o
     * mesmo par de ids, os dois descartam a mesma conexao, sem precisar negociar.
     */
    private boolean vencemos(PeerConnection nova, PeerConnection existente, String remotoId) {
        String iniciadorNova = nova.initiatedByMe() ? node.peerId() : remotoId;
        String iniciadorExistente = existente.initiatedByMe() ? node.peerId() : remotoId;
        return iniciadorNova.compareTo(iniciadorExistente) < 0;
    }

    /** Envia ao recem-chegado os peers que ja conhecemos, menos ele mesmo. E o que fecha a malha. */
    private void enviarPeerList(PeerConnection destino, String peerIdDoDestino) {
        List<PeerInfo> outros = new ArrayList<>();
        for (PeerInfo p : manager.peersConectados()) {
            if (!p.peerId().equals(peerIdDoDestino)) {
                outros.add(p);
            }
        }
        if (outros.isEmpty()) {
            return;
        }
        try {
            enviar(destino, Message.controle(MessageType.PEER_LIST, node.info(), codec.encodePeerList(outros)));
        } catch (IOException e) {
            avisarErro("Falha ao enviar a lista de peers: " + e.getMessage());
        }
    }

    private void tratarPeerList(Message m) {
        List<PeerInfo> lista;
        try {
            lista = codec.decodePeerList(m.texto());
        } catch (IOException e) {
            avisarErro("Lista de peers recebida em formato invalido.");
            return;
        }
        for (PeerInfo p : lista) {
            if (p.peerId().equals(node.peerId())) {
                continue; // nos mesmos
            }
            if (manager.established(p.peerId()) != null) {
                continue; // ja conectados
            }
            if (p.host() == null || p.port() <= 0) {
                continue; // entrada incompleta, nao da para discar
            }
            if (dialing.putIfAbsent(p.peerId(), System.currentTimeMillis()) != null) {
                continue; // ja existe um dial em andamento para esse peer
            }
            dialExecutor.submit(() -> {
                try {
                    PeerConnection c = manager.dial(p.host(), p.port());
                    enviar(c, Message.controle(MessageType.HELLO, node.info(), payloadIdentidade()));
                } catch (IOException e) {
                    dialing.remove(p.peerId());
                    avisarErro("Nao foi possivel alcancar " + p.display()
                            + " em " + p.host() + ":" + p.port() + ".");
                }
            });
        }
    }

    // ------------------------------------------------------------------- conversa

    private void tratarConversa(Message m) {
        if (idsVistos.put(m.id(), Boolean.TRUE) != null) {
            return; // ja entregamos esta mensagem
        }
        if (m.isPrivada() && !node.peerId().equals(m.toPeerId())) {
            return; // privada endereçada a outro peer: nao exibe, nao repassa
        }
        entregar(m);
    }

    // --------------------------------------------------------------- manutencao

    /** Roda a cada 5s: manda PING e derruba quem parou de dar noticia (A7). */
    private void rotinaDeManutencao() {
        if (!rodando) {
            return;
        }
        long agora = System.currentTimeMillis();
        dialing.entrySet().removeIf(e -> agora - e.getValue() > LIMITE_SEM_NOTICIAS_MS);
        Message ping = Message.controle(MessageType.PING, node.info(), null);
        for (PeerConnection c : manager.all()) {
            if (agora - c.lastSeen() > LIMITE_SEM_NOTICIAS_MS) {
                // O socket pode continuar "aberto" apos queda de rede ou maquina desligada:
                // sem este limite, o peer ficaria para sempre na lista da interface.
                removerEAnunciar(c);
                manager.drop(c);
                continue;
            }
            if (!enviar(c, ping)) {
                removerEAnunciar(c);
                manager.drop(c);
            }
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private void removerEAnunciar(PeerConnection conexao) {
        PeerInfo remoto = conexao.remote();
        conexao.close();
        if (remoto == null) {
            return; // caiu antes do handshake: nunca apareceu na interface
        }
        manager.removeIfCurrent(conexao);
        dialing.remove(remoto.peerId());

        // A saida so e anunciada depois da janela de graca, quando confirmamos que nao foi
        // apenas uma troca de socket (ver GRACA_SAIDA_MS).
        ScheduledExecutorService agenda = manutencao;
        if (agenda == null || agenda.isShutdown()) {
            confirmarSaida(remoto);
            return;
        }
        try {
            agenda.schedule(() -> confirmarSaida(remoto), GRACA_SAIDA_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            confirmarSaida(remoto);
        }
    }

    private void confirmarSaida(PeerInfo remoto) {
        if (manager.established(remoto.peerId()) != null) {
            return; // o peer voltou por outro socket: nunca chegou a sair
        }
        if (anunciados.remove(remoto.peerId())) {
            notificarSaida(remoto);
        }
    }

    private boolean enviar(PeerConnection destino, Message m) {
        try {
            return destino.send(codec.encode(m));
        } catch (IOException e) {
            avisarErro("Falha ao serializar mensagem: " + e.getMessage());
            return false;
        }
    }

    private String payloadIdentidade() {
        try {
            return codec.encodePeer(node.info());
        } catch (IOException e) {
            throw new IllegalStateException("identidade propria deveria sempre serializar", e);
        }
    }

    private void exigirRodando() {
        if (!rodando) {
            throw new IllegalStateException("chame start(...) antes de usar a rede");
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void encerrar(ExecutorService executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static Thread daemon(Runnable r, String nome) {
        Thread t = new Thread(r, nome);
        t.setDaemon(true);
        return t;
    }

    // Os quatro metodos abaixo isolam o listener: excecao lancada pela interface nao pode
    // derrubar a thread que le o socket.

    private void entregar(Message m) {
        try {
            listener.onMessage(m);
        } catch (RuntimeException e) {
            System.err.println("[peer] listener falhou em onMessage: " + e);
        }
    }

    private void notificarEntrada(PeerInfo p) {
        try {
            listener.onPeerJoined(p);
        } catch (RuntimeException e) {
            System.err.println("[peer] listener falhou em onPeerJoined: " + e);
        }
    }

    private void notificarSaida(PeerInfo p) {
        try {
            listener.onPeerLeft(p);
        } catch (RuntimeException e) {
            System.err.println("[peer] listener falhou em onPeerLeft: " + e);
        }
    }

    private void avisarErro(String motivo) {
        try {
            listener.onError(motivo);
        } catch (RuntimeException e) {
            System.err.println("[peer] listener falhou em onError: " + e);
        }
    }

    /** Usado enquanto ninguem registrou um listener, para o nucleo nunca precisar checar null. */
    private static final class ListenerSilencioso implements PeerEventListener {
        @Override public void onMessage(Message m) { }
        @Override public void onPeerJoined(PeerInfo p) { }
        @Override public void onPeerLeft(PeerInfo p) { }
        @Override public void onError(String motivo) { }
    }
}
