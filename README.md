# Chat P2P distribuído

Sistema de chat distribuído ponto a ponto, sem servidor central. Cada instância é ao mesmo
tempo cliente e servidor: escuta em uma porta, aceita conexões e mantém uma conexão TCP direta
com cada outro participante (topologia em **malha completa**).

Disciplina de Sistemas Distribuídos — Universidade de Fortaleza.
Continuação do projeto `com.unifor.br.peer`.

## Estado atual

| Parte | Responsável | Situação |
|---|---|---|
| Contrato entre as camadas | Sprint 0, a dupla | pronto |
| Núcleo P2P (rede, protocolo, malha, roteamento) | Andressa | pronto e verificado |
| Interface JavaFX | João Alex | a fazer — ver [`docs/PESSOA-B.md`](docs/PESSOA-B.md) |

O núcleo já entrega tudo que a interface precisa: entrar em uma malha por qualquer participante,
mensagem para todos, mensagem privada, lista de peers e aviso de entrada e saída.
Enquanto a tela não existe, o cliente de console `ConsoleChat` demonstra o sistema funcionando.

## Como rodar

Requer JDK 17 ou superior. Nenhuma dependência externa: o build não baixa bibliotecas.

```bash
./gradlew build          # compila e roda a verificação
./gradlew verificar      # só a suíte de testes do núcleo
./gradlew run --console=plain --args="andressa 5000"
```

Para simular a rede em uma máquina só, abra três terminais:

```bash
# terminal 1 — primeiro peer da malha
./gradlew run --console=plain --args="p1 5000"

# terminal 2 — entra pela porta do p1
./gradlew run --console=plain --args="p2 5001 127.0.0.1 5000"

# terminal 3 — entra pelo p2, mas passa a enxergar o p1 também
./gradlew run --console=plain --args="p3 5002 127.0.0.1 5001"
```

Entre máquinas diferentes, troque `127.0.0.1` pelo IP da máquina do outro peer e libere a porta
no firewall. Passar `0` como porta faz o sistema escolher uma livre.

Comandos do console: `/peers`, `/msg <prefixo-do-id ou nome> texto`, `/join host porta`, `/sair`.
Qualquer outra linha é enviada para todos.

## Estrutura

```
src/main/java/com/unifor/br/peer/
├── contract/          # o combinado entre as duas pessoas — não mude sozinho
│   ├── PeerNetwork.java        # o que a interface chama
│   ├── PeerEventListener.java  # o que a rede avisa
│   ├── Message.java            # o que trafega no socket
│   ├── MessageType.java
│   └── PeerInfo.java           # identidade de um peer
├── net/               # Pessoa A
│   ├── SocketPeerNetwork.java  # protocolo, handshake, malha, roteamento
│   ├── ConnectionManager.java  # aceita e abre conexões, mantém o mapa de quem está vivo
│   ├── PeerConnection.java     # um socket encapsulado
│   ├── PeerNode.java           # identidade local
│   ├── ProtocolCodec.java      # mensagem <-> linha JSON
│   └── Json.java               # leitor/escritor JSON mínimo
├── demo/
│   └── ConsoleChat.java        # cliente temporário, some quando a tela existir
└── ui/                # Pessoa B — a criar

src/test/java/com/unifor/br/peer/testes/
└── SuiteDeTestes.java          # sobe peers de verdade em portas locais
```

## Verificação

`./gradlew verificar` sobe peers reais em portas locais e cobre:

- ida e volta do protocolo com aspas, barra invertida, quebra de linha, acento e emoji;
- JSON aninhado da `PEER_LIST` dentro do campo `texto`;
- linha malformada e tipo desconhecido viram erro tratado, não derrubam a conexão;
- P3 entra pelo P2 e passa a enxergar P1 (a malha fecha sozinha);
- conexão simultânea nos dois sentidos termina com uma única conexão viva, nos dois lados;
- peer que tenta conectar em si mesmo é recusado;
- broadcast chega a todos exatamente uma vez;
- privada chega ao destinatário e não aparece no terceiro;
- saída de um peer avisa os outros e não parte a malha;
- porta ocupada vira erro tratável, com a porta no texto para a tela mostrar.

O roteiro de aceite entre processos separados (três terminais, incluindo saída e volta de um peer)
está descrito em [`docs/PROTOCOLO.md`](docs/PROTOCOLO.md).

## Decisões de projeto

**Sem Spring Boot.** O projeto original trazia `spring-boot-starter` sem usar nenhum bean, e o
contexto aberto impediria a JVM de encerrar quando a janela JavaFX fechasse. Foi removido.

**Sem dependência externa.** O JSON do protocolo é tratado por uma classe interna de ~200 linhas
em vez de Jackson. O build compila e roda em qualquer máquina, sem depender de rede ou proxy —
o que importa quando duas pessoas e um professor precisam executar o mesmo projeto. Trocar por
Jackson depois mexe só em `ProtocolCodec`.

**Malha completa, não anel nem estrela.** Sem nó central para virar ponto único de falha, e sem
repasse de mensagem: cada peer entrega direto a cada outro. O custo é _n(n-1)/2_ conexões, aceitável
para o número de participantes de uma demonstração.

**`peerId` em vez de username como chave.** Dois usuários podem escolher o mesmo nome. O UUID é o
que endereça mensagem privada e o que desempata conexão duplicada.
