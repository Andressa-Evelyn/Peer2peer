# Protocolo do chat P2P

Documento de referência da camada de rede. Serve para o relatório e para quem for mexer no núcleo.

## Transporte

TCP, uma conexão persistente por par de peers. Cada mensagem é **uma linha JSON terminada em `\n`**,
codificada em UTF-8 nos dois sentidos. O escritor escapa quebra de linha, então uma mensagem com
Enter no meio continua ocupando uma linha só e `BufferedReader.readLine()` nunca devolve meia mensagem.

## Formato da mensagem

```json
{"id":"9f2c…","tipo":"CHAT","fromPeerId":"47858a19-…","fromUser":"andressa",
 "toPeerId":null,"texto":"bom dia","timestamp":1756950591123}
```

| Campo | Uso |
|---|---|
| `id` | UUID da mensagem. Base do descarte de duplicata. |
| `tipo` | Um dos sete tipos abaixo. Tipo desconhecido → linha descartada. |
| `fromPeerId` | Quem originou. |
| `fromUser` | Nome de exibição, para a interface não precisar consultar a lista de peers. |
| `toPeerId` | Só em `PRIVATE`. Nulo nos demais. |
| `texto` | Conteúdo da conversa, ou payload JSON nas mensagens de controle. |
| `timestamp` | Epoch millis na origem. Relógios não são sincronizados entre peers. |

### Tipos

| Tipo | Direção | `texto` carrega |
|---|---|---|
| `HELLO` | quem abriu a conexão → quem recebeu | `PeerInfo` do remetente em JSON |
| `HELLO_ACK` | resposta | `PeerInfo` de quem recebeu |
| `PEER_LIST` | quem recebeu → recém-chegado | array JSON de `PeerInfo` |
| `CHAT` | todos | texto da conversa |
| `PRIVATE` | um destinatário | texto da conversa |
| `LEAVE` | ao encerrar | nada |
| `PING` | a cada 5s | nada |

`PeerInfo` é `{"peerId","username","host","port"}`, onde `port` é a **porta de escuta**, nunca a
porta efêmera do socket.

## Entrada na malha

```
P3 (novo)                         P2 (já na malha)                P1
   |  HELLO(P3, porta 5002)  ---->  |                              |
   |  <---- HELLO_ACK(P2)           |                              |
   |  <---- PEER_LIST([P1])         |                              |
   |  HELLO(P3) ---------------------------------------------->    |
   |  <---------------------------------------- HELLO_ACK(P1)      |
```

Ao final, P3 tem conexão direta com P1 e P2, mesmo tendo digitado só o endereço de P2.

Três regras que sustentam isso:

1. **O host confiável é o do socket, não o anunciado.** Quem recebe o `HELLO` preenche o `host`
   do `PeerInfo` com o endereço remoto real da conexão. Um peer que anunciasse `127.0.0.1` deixaria
   os outros incapazes de alcançá-lo. Só a porta de escuta vem do payload, porque a porta do socket
   de entrada é efêmera e não serve para reconectar.
2. **A `PEER_LIST` não inclui quem a recebe nem quem a envia.** O remetente já está conectado ao
   destinatário; mandar a si mesmo geraria conexão duplicada garantida.
3. **Auto-conexão é recusada.** Se o `peerId` anunciado for o próprio, a conexão é fechada.

## Conexão duplicada

A e B discam um para o outro no mesmo instante: sobram dois sockets para o mesmo par, e cada
mensagem chegaria duas vezes.

Regra de desempate: **sobrevive a conexão iniciada pelo peer de menor `peerId`.** Os dois lados
comparam o mesmo par de UUIDs e chegam à mesma conclusão, sem precisar negociar nada.

O lado que descarta o socket perdedor marca a conexão como descartada, para o fechamento não ser
confundido com queda. Do outro lado, o socket morre sem aviso — por isso a saída de um peer só é
anunciada depois de uma janela de confirmação de 300 ms, checando se ele continua alcançável por
outro socket. Sem essa janela, a lista da interface pisca "fulano saiu / fulano entrou" a cada
conexão simultânea.

Os avisos de entrada e saída são idempotentes: a interface recebe no máximo uma entrada e uma
saída por peer, independentemente de quantos sockets foram abertos e trocados por baixo.

## Saída e detecção de queda

- **Saída limpa:** `LEAVE` é enviado a todos antes de fechar, inclusive quando o processo termina
  por Ctrl+C (shutdown hook). Os outros removem o peer na hora.
- **Queda sem aviso** (cabo, máquina desligada, processo morto): `PING` a cada 5 s e limite de
  20 s sem nenhuma mensagem daquele peer. Estourou o limite, a conexão é derrubada e a saída
  anunciada. Sem isso, um socket que continua "aberto" após queda de rede deixaria o peer preso
  na lista para sempre.

## Roteiro de aceite

Três instâncias, portas 5000, 5001 e 5002. Executado com sucesso entre processos separados.

| # | Passo | O que prova |
|---|---|---|
| 1 | P2 conecta em P1 | handshake básico |
| 2 | P3 conecta em **P2**, e `/peers` mostra P1 e P2 | a malha fecha sozinha |
| 3 | P1 envia ao grupo | broadcast, com remetente vindo do protocolo |
| 4 | P1 envia privada a P3 | chega em P3, **não** aparece em P2 |
| 5 | P2 fecha | P1 e P3 anunciam a saída e continuam conversando |
| 6 | P2 volta conectando só em P1 | volta a enxergar P3 pela `PEER_LIST` |
| 7 | Duas máquinas na mesma rede, com IP real | é onde o firewall aparece |

Os passos 1 a 6 estão cobertos pela suíte automatizada (`./gradlew verificar`), além do
teste de corrida de conexão simultânea, que não dá para reproduzir à mão de forma confiável.
O passo 7 precisa ser feito manualmente, com as duas máquinas — deixar para a véspera da
apresentação é o erro clássico.

## Limitações conhecidas

- **NAT e redes diferentes.** O endereço que um peer distribui na `PEER_LIST` é o que ele enxerga.
  Peers atrás de NATs distintos não se alcançam sem redirecionamento de porta. Para a demonstração,
  todos na mesma rede local.
- **Sem persistência.** O histórico vive na memória do processo; fechar perde a conversa.
- **Sem criptografia.** As mensagens trafegam em texto claro. Mensagem "privada" significa que não
  é entregue a terceiros pela aplicação, não que seja sigilosa na rede.
- **Escala.** Malha completa custa _n(n-1)/2_ conexões. Adequado à ordem de dezenas de peers.
