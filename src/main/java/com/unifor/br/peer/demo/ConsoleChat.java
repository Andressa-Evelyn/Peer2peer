package com.unifor.br.peer.demo;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.PeerEventListener;
import com.unifor.br.peer.contract.PeerInfo;
import com.unifor.br.peer.contract.PeerNetwork;
import com.unifor.br.peer.net.SocketPeerNetwork;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cliente de console que exercita o nucleo sem interface grafica.
 *
 * Quando a tela JavaFX estiver pronta, o mainClass do build aponta para ela e esta
 * classe pode ser apagada.
 *
 * <p>Uso:
 * <pre>
 *   ./gradlew run --console=plain
 *   ./gradlew run --console=plain --args="andressa 5000"
 *   ./gradlew run --console=plain --args="joao 5001 localhost 5000"
 * </pre>
 *
 * <p>Comandos: {@code /peers}, {@code /msg &lt;prefixo-do-id&gt; texto}, {@code /join host porta},
 * {@code /sair}. Qualquer outra linha vai para todos.
 */
public final class ConsoleChat {

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) throws Exception {
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        String username = args.length > 0 ? args[0] : perguntar(entrada, "Seu nome: ");
        int porta = args.length > 1
                ? Integer.parseInt(args[1])
                : Integer.parseInt(perguntar(entrada, "Porta para escutar (0 = automatica): "));

        PeerNetwork rede = new SocketPeerNetwork();
        rede.setListener(new ImpressoraDeEventos(rede));

        try {
            rede.start(username, porta);
        } catch (IllegalStateException e) {
            System.out.println("Nao foi possivel iniciar: " + e.getMessage());
            return;
        }
        System.out.println("Escutando em " + rede.self().port() + " como " + rede.self().display());

        // LEAVE tambem quando o processo e encerrado com Ctrl+C.
        Runtime.getRuntime().addShutdownHook(new Thread(rede::shutdown));

        if (args.length > 3) {
            rede.connectTo(args[2], Integer.parseInt(args[3]));
        } else if (args.length == 0) {
            String resposta = perguntar(entrada, "Conectar a um peer existente? (s/N): ");
            if (resposta.equalsIgnoreCase("s")) {
                String host = perguntar(entrada, "Host: ");
                int portaRemota = Integer.parseInt(perguntar(entrada, "Porta: "));
                rede.connectTo(host, portaRemota);
            }
        }

        System.out.println("Comandos: /peers  /msg <prefixo-id> texto  /join host porta  /sair");
        laco(entrada, rede);
        rede.shutdown();
        System.out.println("Ate mais.");
    }

    private static void laco(BufferedReader entrada, PeerNetwork rede) throws Exception {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            linha = linha.trim();
            if (linha.isEmpty()) {
                continue;
            }
            if (linha.equals("/sair")) {
                return;
            }
            if (linha.equals("/peers")) {
                List<PeerInfo> peers = rede.connectedPeers();
                if (peers.isEmpty()) {
                    System.out.println("* nenhum peer conectado");
                }
                peers.forEach(p -> System.out.println("* " + p.display() + " em " + p.host() + ":" + p.port()));
                continue;
            }
            if (linha.startsWith("/join ")) {
                String[] partes = linha.split("\\s+");
                if (partes.length < 3) {
                    System.out.println("uso: /join host porta");
                    continue;
                }
                rede.connectTo(partes[1], Integer.parseInt(partes[2]));
                continue;
            }
            if (linha.startsWith("/msg ")) {
                String[] partes = linha.split("\\s+", 3);
                if (partes.length < 3) {
                    System.out.println("uso: /msg <prefixo-id> texto");
                    continue;
                }
                String alvo = resolver(rede, partes[1]);
                if (alvo == null) {
                    System.out.println("* nenhum peer com id ou nome comecando por " + partes[1]);
                    continue;
                }
                rede.sendPrivate(alvo, partes[2]);
                continue;
            }
            rede.broadcast(linha);
        }
    }

    /** Aceita prefixo do peerId ou o username, para nao precisar digitar o UUID inteiro. */
    private static String resolver(PeerNetwork rede, String termo) {
        for (PeerInfo p : rede.connectedPeers()) {
            if (p.peerId().startsWith(termo) || p.username().equalsIgnoreCase(termo)) {
                return p.peerId();
            }
        }
        return null;
    }

    private static String perguntar(BufferedReader entrada, String pergunta) throws Exception {
        System.out.print(pergunta);
        System.out.flush();
        String resposta = entrada.readLine();
        return resposta == null ? "" : resposta.trim();
    }

    /** Faz aqui o papel que o ChatController da Pessoa B fara na interface. */
    private record ImpressoraDeEventos(PeerNetwork rede) implements PeerEventListener {

        @Override
        public void onMessage(Message m) {
            String hora = HORA.format(Instant.ofEpochMilli(m.timestamp()));
            boolean meu = m.fromPeerId().equals(rede.self().peerId());
            String marca = m.isPrivada() ? (meu ? "[privada para] " : "[privada de] ") : "";
            System.out.println(hora + " " + marca + m.fromUser() + ": " + m.texto());
        }

        @Override
        public void onPeerJoined(PeerInfo p) {
            System.out.println("* " + p.display() + " entrou na malha");
        }

        @Override
        public void onPeerLeft(PeerInfo p) {
            System.out.println("* " + p.display() + " saiu");
        }

        @Override
        public void onError(String motivo) {
            System.out.println("! " + motivo);
        }
    }
}
