package com.unifor.br.peer.testes;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.MessageType;
import com.unifor.br.peer.contract.PeerInfo;
import com.unifor.br.peer.net.ProtocolCodec;
import com.unifor.br.peer.net.SocketPeerNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.unifor.br.peer.testes.Verificador.assentar;
import static com.unifor.br.peer.testes.Verificador.confirmar;
import static com.unifor.br.peer.testes.Verificador.esperarAte;
import static com.unifor.br.peer.testes.Verificador.igual;

/**
 * Suite do nucleo P2P (tarefa A8).
 *
 * <p>Sobe peers de verdade, em portas locais escolhidas pelo sistema (porta 0), e exercita o
 * mesmo caminho que a interface vai usar. Nao ha mock de socket: o que passa aqui e o que
 * roda na apresentacao.
 *
 * <p>Cada teste cobre um item do roteiro de aceite combinado com a equipe.
 */
public final class SuiteDeTestes {

    private static final String LOCAL = "127.0.0.1";

    public static void main(String[] args) {
        Verificador v = new Verificador();
        System.out.println("Verificacao do nucleo P2P");
        System.out.println();

        protocolo(v);
        malha(v);
        conversa(v);
        resiliencia(v);

        System.exit(v.resumo() ? 0 : 1);
    }

    // ------------------------------------------------------------ A4: protocolo

    private static void protocolo(Verificador v) {
        ProtocolCodec codec = new ProtocolCodec();
        PeerInfo eu = new PeerInfo("id-1", "andressa", "127.0.0.1", 5000);

        v.teste("codec: mensagem sobrevive a aspas, barra, quebra de linha e emoji", () -> {
            String dificil = "ela disse \"oi\" \\ e apertou\nEnter no meio \t da frase 🙂 acentuação";
            Message original = Message.chat(eu, dificil);
            String linha = codec.encode(original);

            confirmar(!linha.contains("\n"), "o JSON nao pode conter quebra de linha crua: "
                    + "readLine() partiria a mensagem em duas");

            Message volta = codec.decode(linha);
            igual(dificil, volta.texto(), "texto deveria voltar identico");
            igual(original.id(), volta.id(), "id deveria voltar identico");
            igual(MessageType.CHAT, volta.tipo(), "tipo deveria voltar identico");
            igual(original.timestamp(), volta.timestamp(), "timestamp deveria voltar identico");
            igual(null, volta.toPeerId(), "CHAT nao tem destinatario");
        });

        v.teste("codec: privada preserva o destinatario", () -> {
            Message m = Message.privado(eu, "id-2", "so para voce");
            Message volta = codec.decode(codec.encode(m));
            igual("id-2", volta.toPeerId(), "destinatario deveria voltar identico");
            confirmar(volta.isPrivada(), "deveria ser reconhecida como privada");
        });

        v.teste("codec: PeerInfo e lista de peers", () -> {
            PeerInfo volta = codec.decodePeer(codec.encodePeer(eu));
            igual(eu, volta, "PeerInfo deveria voltar identico");

            List<PeerInfo> lista = List.of(eu, new PeerInfo("id-2", "joão", "192.168.0.7", 5001));
            List<PeerInfo> voltaLista = codec.decodePeerList(codec.encodePeerList(lista));
            igual(lista, voltaLista, "lista de peers deveria voltar identica");
        });

        v.teste("codec: lista de peers viaja dentro do campo texto de uma mensagem", () -> {
            // PEER_LIST carrega JSON dentro de JSON; e o ponto mais facil de quebrar o escape.
            List<PeerInfo> lista = List.of(eu, new PeerInfo("id-2", "maria \"a dev\"", "10.0.0.3", 6000));
            Message m = Message.controle(MessageType.PEER_LIST, eu, codec.encodePeerList(lista));
            Message volta = codec.decode(codec.encode(m));
            igual(lista, codec.decodePeerList(volta.texto()), "lista aninhada deveria voltar identica");
        });

        v.teste("codec: linha malformada e tipo desconhecido viram IOException", () -> {
            recusa(codec, "isso nao e json");
            recusa(codec, "{\"id\":\"1\"");
            recusa(codec, "{\"id\":\"1\",\"tipo\":\"CHAT\"}");            // sem fromPeerId
            recusa(codec, "{\"id\":\"1\",\"tipo\":\"FOFOCA\",\"fromPeerId\":\"x\"}"); // tipo inexistente
        });
    }

    private static void recusa(ProtocolCodec codec, String linha) {
        try {
            codec.decode(linha);
            throw new AssertionError("deveria ter recusado a linha: " + linha);
        } catch (IOException esperado) {
            // exatamente o que queremos
        }
    }

    // ----------------------------------------------------------- A5: malha

    private static void malha(Verificador v) {
        v.teste("malha: P3 entra por P2 e passa a enxergar P1", () -> {
            Rede rede = new Rede();
            try {
                var p1 = rede.criar("p1");
                var p2 = rede.criar("p2");
                var p3 = rede.criar("p3");

                p2.conectarEm(p1);
                esperarAte(() -> p1.rede.connectedPeers().size() == 1, "P1 e P2 conectados");

                // P3 so conhece o endereco de P2. Se a malha fecha, ele descobre P1 sozinho.
                p3.conectarEm(p2);

                esperarAte(() -> p3.rede.connectedPeers().size() == 2, "P3 enxergando P1 e P2");
                esperarAte(() -> p1.rede.connectedPeers().size() == 2, "P1 enxergando P2 e P3");
                esperarAte(() -> p2.rede.connectedPeers().size() == 2, "P2 enxergando P1 e P3");

                confirmar(p3.coletor.entradas.size() == 2,
                        "P3 deveria ter anunciado exatamente 2 entradas, e nao "
                                + p3.coletor.entradas.size());
            } finally {
                rede.encerrar();
            }
        });

        v.teste("malha: conexao simultanea nos dois sentidos deixa uma unica conexao viva", () -> {
            Rede rede = new Rede();
            try {
                var p1 = rede.criar("p1");
                var p2 = rede.criar("p2");

                // O caso de corrida: os dois discam ao mesmo tempo.
                p1.conectarEm(p2);
                p2.conectarEm(p1);

                esperarAte(() -> p1.rede.connectedPeers().size() == 1
                        && p2.rede.connectedPeers().size() == 1, "uma conexao de cada lado");
                assentar();

                igual(1, p1.rede.connectedPeers().size(), "P1 deveria ter exatamente 1 peer");
                igual(1, p2.rede.connectedPeers().size(), "P2 deveria ter exatamente 1 peer");
                igual(1, p1.coletor.entradas.size(), "P1 nao deveria anunciar a entrada duas vezes");
                igual(1, p2.coletor.entradas.size(), "P2 nao deveria anunciar a entrada duas vezes");

                // Se cada lado tivesse guardado um socket diferente, um dos sentidos ficaria mudo.
                p1.rede.broadcast("de p1 para p2");
                esperarAte(() -> p2.coletor.quantasVezesRecebeu("de p1 para p2") == 1, "P2 recebeu de P1");
                p2.rede.broadcast("de p2 para p1");
                esperarAte(() -> p1.coletor.quantasVezesRecebeu("de p2 para p1") == 1, "P1 recebeu de P2");
            } finally {
                rede.encerrar();
            }
        });

        v.teste("malha: peer que tenta conectar em si mesmo e recusado", () -> {
            Rede rede = new Rede();
            try {
                var p1 = rede.criar("p1");
                p1.rede.connectTo(LOCAL, p1.rede.self().port());
                assentar();
                igual(0, p1.rede.connectedPeers().size(), "auto-conexao nao pode virar peer");
            } finally {
                rede.encerrar();
            }
        });
    }

    // -------------------------------------------------------- A6: roteamento

    private static void conversa(Verificador v) {
        v.teste("broadcast: a mensagem chega a todos, uma unica vez, com o remetente correto", () -> {
            Rede rede = new Rede();
            try {
                var trio = rede.trioConectado();
                var p1 = trio.get(0);
                var p2 = trio.get(1);
                var p3 = trio.get(2);

                p1.rede.broadcast("bom dia a todos");

                esperarAte(() -> p2.coletor.quantasVezesRecebeu("bom dia a todos") == 1, "P2 recebeu");
                esperarAte(() -> p3.coletor.quantasVezesRecebeu("bom dia a todos") == 1, "P3 recebeu");
                esperarAte(() -> p1.coletor.quantasVezesRecebeu("bom dia a todos") == 1,
                        "P1 ve o eco da propria mensagem");
                assentar();

                igual(1L, p2.coletor.quantasVezesRecebeu("bom dia a todos"), "sem duplicata em P2");
                igual(1L, p3.coletor.quantasVezesRecebeu("bom dia a todos"), "sem duplicata em P3");

                Message recebida = p3.coletor.mensagens.get(0);
                igual(p1.rede.self().peerId(), recebida.fromPeerId(), "remetente vem do protocolo");
                igual("p1", recebida.fromUser(), "username vem do protocolo, nao colado no texto");
            } finally {
                rede.encerrar();
            }
        });

        v.teste("privada: chega ao destinatario e nao vaza para o terceiro", () -> {
            Rede rede = new Rede();
            try {
                var trio = rede.trioConectado();
                var p1 = trio.get(0);
                var p2 = trio.get(1);
                var p3 = trio.get(2);

                p1.rede.sendPrivate(p3.rede.self().peerId(), "segredo entre nos dois");

                esperarAte(() -> p3.coletor.quantasVezesRecebeu("segredo entre nos dois") == 1,
                        "P3 recebeu a privada");
                assentar(); // tempo de sobra para a mensagem vazar, se fosse vazar

                igual(0L, p2.coletor.quantasVezesRecebeu("segredo entre nos dois"),
                        "P2 nao pode ver a privada de P1 para P3");
                confirmar(p3.coletor.mensagens.get(0).isPrivada(),
                        "a interface precisa conseguir distinguir privada de publica");
                igual(1L, p1.coletor.quantasVezesRecebeu("segredo entre nos dois"),
                        "quem enviou tambem ve a propria privada");
            } finally {
                rede.encerrar();
            }
        });

        v.teste("privada para peer inexistente vira erro na interface, nao excecao", () -> {
            Rede rede = new Rede();
            try {
                var p1 = rede.criar("p1");
                p1.rede.sendPrivate("peer-que-nunca-existiu", "oi");
                esperarAte(() -> !p1.coletor.erros.isEmpty(), "erro reportado ao listener");
            } finally {
                rede.encerrar();
            }
        });
    }

    // ------------------------------------------------------ A7: saida e queda

    private static void resiliencia(Verificador v) {
        v.teste("saida: fechar P2 avisa os outros e nao parte a malha", () -> {
            Rede rede = new Rede();
            try {
                var trio = rede.trioConectado();
                var p1 = trio.get(0);
                var p2 = trio.get(1);
                var p3 = trio.get(2);
                String idDoP2 = p2.rede.self().peerId();

                p2.rede.shutdown();

                esperarAte(() -> p1.coletor.saiuPeer(idDoP2), "P1 soube que P2 saiu");
                esperarAte(() -> p3.coletor.saiuPeer(idDoP2), "P3 soube que P2 saiu");
                esperarAte(() -> p1.rede.connectedPeers().size() == 1, "P1 removeu P2 da lista");
                esperarAte(() -> p3.rede.connectedPeers().size() == 1, "P3 removeu P2 da lista");

                // O que sobrou da malha continua funcionando.
                p1.rede.broadcast("ainda estamos aqui");
                esperarAte(() -> p3.coletor.quantasVezesRecebeu("ainda estamos aqui") == 1,
                        "P1 e P3 continuam conversando sem P2");
            } finally {
                rede.encerrar();
            }
        });

        v.teste("saida: shutdown chamado duas vezes nao lanca", () -> {
            Rede rede = new Rede();
            try {
                var p1 = rede.criar("p1");
                p1.rede.shutdown();
                p1.rede.shutdown();
            } finally {
                rede.encerrar();
            }
        });

        v.teste("porta ocupada nao derruba a aplicacao: vira IllegalStateException tratavel", () -> {
            Rede rede = new Rede();
            try {
                var p1 = rede.criar("p1");
                int portaOcupada = p1.rede.self().port();

                SocketPeerNetwork intruso = new SocketPeerNetwork();
                rede.registrar(intruso);
                try {
                    intruso.start("intruso", portaOcupada);
                    throw new AssertionError("deveria ter recusado a porta ja ocupada");
                } catch (IllegalStateException esperado) {
                    confirmar(esperado.getMessage().contains(String.valueOf(portaOcupada)),
                            "a mensagem de erro precisa dizer qual porta falhou, para a tela mostrar");
                }
            } finally {
                rede.encerrar();
            }
        });
    }

    // ------------------------------------------------------------- utilitarios

    /** Guarda os peers criados no teste e garante que todos sejam fechados no fim. */
    private static final class Rede {

        private final List<SocketPeerNetwork> criados = new ArrayList<>();

        Peer criar(String nome) {
            Coletor coletor = new Coletor(nome);
            SocketPeerNetwork rede = new SocketPeerNetwork();
            rede.setListener(coletor);
            rede.start(nome, 0); // porta 0: o sistema escolhe uma livre, testes nunca colidem
            criados.add(rede);
            return new Peer(rede, coletor);
        }

        void registrar(SocketPeerNetwork rede) {
            criados.add(rede);
        }

        /** P1, P2 e P3 em malha fechada, com P3 entrando por P2. */
        List<Peer> trioConectado() throws InterruptedException {
            Peer p1 = criar("p1");
            Peer p2 = criar("p2");
            Peer p3 = criar("p3");
            p2.conectarEm(p1);
            esperarAte(() -> p1.rede.connectedPeers().size() == 1, "P1 e P2 conectados");
            p3.conectarEm(p2);
            esperarAte(() -> p1.rede.connectedPeers().size() == 2
                    && p2.rede.connectedPeers().size() == 2
                    && p3.rede.connectedPeers().size() == 2, "malha de tres fechada");
            return List.of(p1, p2, p3);
        }

        void encerrar() {
            for (SocketPeerNetwork rede : criados) {
                try {
                    rede.shutdown();
                } catch (RuntimeException ignorado) {
                    // encerramento de teste nunca deve mascarar a falha real
                }
            }
        }
    }

    private record Peer(SocketPeerNetwork rede, Coletor coletor) {
        void conectarEm(Peer outro) {
            rede.connectTo(LOCAL, outro.rede.self().port());
        }
    }
}
