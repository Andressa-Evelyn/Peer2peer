# Handoff para a Pessoa B — interface JavaFX

O núcleo está pronto e verificado. Este documento é o que você precisa para começar sem
precisar ler o código de rede.

## O que você recebe

`com.unifor.br.peer.net.SocketPeerNetwork` implementa `PeerNetwork`. Você nunca importa nada do
pacote `net`além dessa classe — programe contra as interfaces do pacote `contract`.

```java
PeerNetwork rede = new SocketPeerNetwork();
rede.setListener(meuChatController);   // antes do start
rede.start("andressa", 5000);          // porta 0 = o sistema escolhe uma livre
rede.self().port();                    // a porta realmente usada, para mostrar na tela

rede.connectTo("192.168.0.7", 5000);   // não bloqueia; a confirmação vem em onPeerJoined
rede.broadcast("bom dia");
rede.sendPrivate(peerId, "só entre nós");
rede.connectedPeers();                 // List<PeerInfo>, pode ser lida a qualquer momento
rede.shutdown();                       // avisa a malha e fecha tudo
```

E você implementa `PeerEventListener` (quatro métodos: `onMessage`, `onPeerJoined`, `onPeerLeft`,
`onError`).

## As três regras que evitam os bugs previstos no plano

**1. Toda atualização de tela dentro de `Platform.runLater`.** Os quatro callbacks chegam em
threads de rede. Tocar em um nó JavaFX fora da thread da UI lança `IllegalStateException` ou,
pior, corrompe a lista sem erro nenhum.

```java
@Override
public void onMessage(Message m) {
    Platform.runLater(() -> mensagens.add(m));   // mensagens é uma ObservableList
}
```

**2. Nada de bloquear dentro do callback.** Um callback lento segura a leitura daquele socket.
Adicione na lista e volte.

**3. `shutdown()` no fechamento da janela.** Sem ele, os outros peers só descobrem sua saída
quando o `PING` estourar, 20 segundos depois.

```java
primaryStage.setOnCloseRequest(e -> rede.shutdown());
```

## O que muda em relação ao plano combinado

Três ajustes feitos durante a implementação, todos aditivos:

| Combinado na Sprint 0 | Como ficou | Por quê |
|---|---|---|
| `PeerNetwork` sem `setListener` | `setListener(PeerEventListener)` | o listener precisava entrar antes do `start` |
| `PeerNetwork` sem identidade própria | `PeerInfo self()` | a tela mostra o próprio nome e a porta real (importante quando se usa porta 0) |
| `enum Type` aninhado | `enum MessageType` no topo | evitar um tipo chamado `Type` solto no pacote |

O `record Message` e o `PeerEventListener` estão exatamente como combinados.

Uma característica a considerar no layout: **quem envia recebe a própria mensagem de volta**
(`onMessage` com `fromPeerId == self().peerId()`), tanto no broadcast quanto na privada. Assim a
tela não precisa inserir a mensagem local por um caminho diferente do das recebidas — mas você
precisa alinhar as suas à direita comparando o `fromPeerId`.

## Suas tarefas

- **B1 — JavaFX no build.** Em `build.gradle`, descomente o plugin `org.openjfx.javafxplugin`, o
  bloco `javafx { ... }`, e troque o `mainClass` para a sua classe. Verifique `./gradlew run` em
  Windows e Linux.
- **B2 — Tela de entrada.** Nome, porta de escuta e `host:porta` para entrar numa rede existente.
  Valide antes de chamar o núcleo: nome vazio e porta fora de 1–65535. Porta ocupada já volta como
  `IllegalStateException` com o número da porta na mensagem — mostre isso, não o stack trace.
- **B3 — Tela do chat.** Lista de peers à esquerda, histórico ao centro, campo de envio embaixo.
  Envio por Enter, rolagem automática, mensagens de sistema para entrada e saída.
- **B4 — Conversa privada.** Selecionar um peer da lista abre a conversa direta. Distinção visual
  clara entre o que todos veem e o que só o destinatário vê — use `m.isPrivada()`.
- **B5 — ChatController.** Implementa `PeerEventListener` seguindo as três regras acima.
- **B6 — FakePeerNetwork.** Faça primeiro, na primeira semana. Com ele a tela roda sozinha e você
  não fica esperando nada. Esqueleto:

  ```java
  public class FakePeerNetwork implements PeerNetwork {
      private PeerEventListener l;
      private final PeerInfo eu = new PeerInfo("fake-eu", "eu", "127.0.0.1", 5000);
      private final List<PeerInfo> peers = new ArrayList<>(List.of(
              new PeerInfo("fake-1", "joão", "127.0.0.1", 5001),
              new PeerInfo("fake-2", "maria", "127.0.0.1", 5002)));

      public void setListener(PeerEventListener l) { this.l = l; }
      public void start(String username, int porta) { peers.forEach(l::onPeerJoined); }
      public void broadcast(String texto) { l.onMessage(Message.chat(eu, texto)); }
      // ... e assim por diante
  }
  ```

  Troque por `new SocketPeerNetwork()` quando a tela estiver de pé: nenhuma outra linha muda.
- **B7 — Estilo e erros.** CSS, timestamps (o `timestamp` vem em epoch millis), indicador de
  conectado/desconectado, e `onError` virando aviso legível.
- **B8 — Empacotamento e README.** Jar executável e passo a passo para subir três instâncias.

## Enquanto isso

`./gradlew run --console=plain --args="p1 5000"` sobe o cliente de console. Rode três terminais
(ver README) para ver na prática o comportamento que a sua tela precisa refletir: entrada de peer,
broadcast, privada e saída. Quando a tela existir, `demo/ConsoleChat.java` pode ser apagada.

## Combinado de trabalho

- Branch `feature/ui`. `main` sempre executável.
- Se precisar de algo novo no contrato, **fale antes de mudar** — alterar `contract/` sozinho quebra
  os dois lados e a suíte de testes.
- `./gradlew verificar` precisa continuar verde antes de qualquer merge.
