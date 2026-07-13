# Falas da Apresentação — sd-runner (parte do Lucas)

> **Formato:** texto corrido, para falar (não ler palavra por palavra).
> **Estilo:** mesmo tom da referência `conceitos-apresentacao.md`.
> **Sua parte:** introdução (improviso) + slides **5, 9, 10, 11, 13, 14, 15, 16**.
> **A outra parte** (slides 1–4, 6, 7, 8, 12) fica com o Luiz Miguel e segue o
> conteúdo do `conceitos-apresentacao.md`.

---

## ⏱️ Orçamento de tempo (~20 min no total)

| Bloco | Quem | Tempo |
|---|---|---|
| Introdução / inspiração (improviso) | **você** | ~2:00 |
| Contexto + Agenda + Motivação + X Window (slides 1–4, 6–8) | Luiz Miguel | ~8:00 |
| **Slide 5 — Arquitetura** | **você** | ~1:00 |
| **Slide 9 — Docker multi-stage** | **você** | ~1:00 |
| **Slide 10 — MySQL: provisionamento** | **você** | ~1:00 |
| **Slide 11 — MySQL: DooD e o trade-off** | **você** | ~1:00 |
| Slide 12 — Persistência: dois níveis | Luiz Miguel | ~1:00 |
| **Slide 13 — Persistência: criptografia** | **você** | ~1:15 |
| **Slide 14 — Síntese DEV × PROD** | **você** | ~1:00 |
| **Slide 15 — Fechamento** | **você** | ~0:45 |
| **Slide 16 — Demo (backup)** | **você** | ~0:30 (só se sobrar tempo) |

> Sua parte falada dá ~7 min + 2 min de intro ≈ **9 min**; a parte do Luiz ≈ **11 min**.
> Total ≈ **20 min**, com folga para respirar e para perguntas.

---

## Introdução (improviso, ~2 min)

*Não precisa preparar — fale da inspiração com suas palavras.* Só um lembrete de
gancho para emendar no primeiro slide técnico: depois de contar a inspiração,
passe a palavra dizendo algo como *"o Luiz vai começar pelo contexto e pela
parte mais incomum, o X Window, e eu volto na arquitetura."*

---

## Slide 5 — Arquitetura: cinco módulos

> `01 · ARQUITETURA` — "Cinco módulos, uma responsabilidade cada"
> ⏱️ ~1:00

Antes de entrar em cada tecnologia, vale mostrar como o projeto está organizado,
porque a estrutura já conta a história. O sd-runner é um projeto Maven
multi-módulo em Java 17, e cada módulo tem uma responsabilidade única. O `core` é
o núcleo: é ele que gerencia os pools de conexão JDBC com o HikariCP e executa as
queries com streaming e com a possibilidade de abortar no meio da execução. O
`store` cuida da persistência local em SQLite e da criptografia protegida por uma
passphrase mestra. O `provisioner` é quem cria o MySQL do zero, seja em container,
seja em VM. O `ui` é a interface gráfica em Swing — as conexões, o editor de SQL e
a exibição dos resultados. E o `cli` é um cliente de linha de comando que serve
como smoke-test, empacotado num uber-jar.

O que interessa nesse grafo à direita é a direção das dependências: tudo se apoia
no `core`. A `ui` depende de `core`, `store` e `provisioner`, mas todos, no fim,
convergem para o núcleo de conexão e consulta. Guardem essa separação, porque cada
pilar que eu vou apresentar corresponde exatamente a um desses módulos.

---

## Slide 9 — Docker: imagem multi-stage

> `03 · DOCKER` — "Uma imagem enxuta com multi-stage build"
> ⏱️ ~1:00

Agora, sobre como a aplicação é empacotada. O Dockerfile do projeto usa uma
técnica chamada multi-stage build, que é um padrão importante para imagens de
produção. A ideia é ter dois estágios separados dentro do mesmo arquivo. O
primeiro estágio, o de build, parte de uma imagem completa com Maven e compila
apenas a `ui` e as dependências dela — é o `mvn -pl modules/ui -am package` — e o
resultado é o `sd-runner-app.jar`. O segundo estágio, o de runtime, parte de um
JRE enxuto e instala só o que é necessário para rodar: o Java, mais a pilha X que
o Luiz mostrou — Xvfb, x11vnc, fluxbox, noVNC e websockify — e o Docker CLI, que a
gente vai usar daqui a pouco.

E aqui está o ponto central: entre um estágio e outro, a gente copia **apenas o
`.jar`**. O Maven, o código-fonte, as dependências de build — nada disso vai para
a imagem final. Eles ficam no primeiro estágio e são descartados. O ganho é
duplo: a imagem fica menor e com menos superfície de ataque, porque não carrega
uma toolchain de compilação inteira dentro de algo que só precisa executar.

---

## Slide 10 — MySQL: provisionamento automático

> `04 · MYSQL` — "A app cria o banco — em container ou em VM"
> ⏱️ ~1:00

Uma coisa que diferencia o sd-runner é que ele não só conecta num MySQL que já
existe — ele consegue provisionar um do zero. E são dois modos. O
`DockerMysqlProvisioner` sobe um container MySQL com um volume nomeado, que é o
que garante a persistência dos dados, espera o banco ficar realmente pronto — o
que a gente chama de readiness — e aplica um seed inicial. Já o
`VmMysqlProvisioner` segue outro caminho: ele gera um arquivo cloud-init e delega
o boot da VM a um launcher configurável, como o multipass.

A leitura por trás disso é: o container é rápido e prático, ideal para
desenvolvimento; a VM é mais isolada, mais adequada para produção. E reparem no
detalhe do readiness — é exatamente a mesma ideia que apareceu no Xvfb, de esperar
o recurso ficar pronto antes de seguir em frente. É um padrão que se repete no
projeto inteiro.

---

## Slide 11 — MySQL: DooD e o trade-off

> `04 · MYSQL · SEGURANÇA` — "Como o container cria outro container? (DooD)"
> ⏱️ ~1:00

Isso levanta uma pergunta que costuma aparecer: se a própria aplicação já está
rodando dentro de um container, como é que ela consegue criar outro container? A
resposta é uma técnica chamada DooD, Docker-out-of-Docker. A gente monta o socket
do Docker do host — o `/var/run/docker.sock` — dentro do container da aplicação.
Quando o provisionador executa um `docker run`, esse comando fala com o socket
montado e cria um container MySQL **irmão**, direto no host — não um container
dentro do container, e sim ao lado.

E aqui vem o trade-off honesto, que eu faço questão de deixar explícito: quem tem
acesso ao socket do Docker tem, na prática, acesso root ao host inteiro. É
poderoso e é perigoso ao mesmo tempo. Por isso esse socket só é montado em
desenvolvimento. Em produção ele fica desabilitado, e o caminho recomendado passa
a ser o modo VM, que não precisa do socket. O projeto não esconde esse custo —
ele o torna visível na própria configuração.

---

## Slide 13 — Persistência: a criptografia

> `05 · PERSISTÊNCIA · CRIPTO` — "Senhas cifradas por uma passphrase mestra"
> ⏱️ ~1:15

O Luiz acabou de mostrar que os perfis de conexão ficam num SQLite local e que as
senhas são cifradas em repouso. Deixa eu explicar como essa cifragem funciona,
porque o design é elegante. As senhas são cifradas com PBE, HMAC-SHA256 e AES-128.
Mas o ponto mais interessante é a chave: cifrar exige uma chave, e essa chave
precisaria ficar guardada em algum lugar. Só que guardar a chave junto do banco
seria inútil — seria como esconder a chave embaixo do tapete.

A solução é que a chave **não é guardada em lugar nenhum**. Ela é derivada, na
hora, da passphrase mestra que o usuário digita ao abrir a aplicação — é o que o
`MasterKeyManager` faz. Enquanto a aplicação está rodando, a chave vive só em
memória; quando a aplicação fecha, a chave some. No disco, no SQLite, ficam apenas
as senhas já cifradas — o arquivo sozinho não revela nada.

Aí surge uma pergunta prática: na próxima vez que o usuário abrir a app, como
saber se a passphrase que ele digitou é a correta, se a gente nunca guardou a
passphrase? A resposta é um verificador — a app cifra um texto conhecido e, na
abertura, tenta decifrá-lo; se der certo, a passphrase está correta. É o mesmo
princípio de uma senha com hash: você não guarda o segredo, guarda uma prova de
que ele está certo. E a consequência disso é direta e por design: se você perde a
passphrase, você perde o acesso aos segredos. Não existe backdoor, nem para o
desenvolvedor. A segurança e a irreversibilidade, aqui, são a mesma coisa.

---

## Slide 14 — Síntese: DEV × PROD

> `SÍNTESE` — "O mesmo sistema, dois modos"
> ⏱️ ~1:00

Se a gente juntar tudo, aparece um fio condutor que costura a apresentação
inteira: o mesmo trade-off entre praticidade e segurança, repetido nos três
pilares. No VNC, em desenvolvimento a tela abre sem senha; em produção, exige
senha e pode ter TLS. Na passphrase, em desenvolvimento tem auto-unlock por
variável de ambiente; em produção, ela é digitada na interface. E na criação de
ambiente, em desenvolvimento usa-se o DooD com o socket do Docker; em produção,
usa-se o modo VM, sem socket.

E o mais bonito é que não são dois sistemas diferentes — é exatamente o mesmo
código atendendo os dois mundos. O que muda é só a configuração, via variáveis de
ambiente. O projeto assume as concessões que faz em desenvolvimento e as fecha
quando vai para produção, de forma explícita.

---

## Slide 15 — Fechamento

> `06 · FECHAMENTO` — "Quatro temas, um sistema coeso"
> ⏱️ ~0:45

Para fechar: os quatro temas não são tópicos soltos, são peças de um mesmo
sistema. O X Window entrega uma app Swing headless no navegador, via Xvfb, x11vnc
e noVNC. O Docker empacota tudo numa imagem multi-stage enxuta e ainda permite que
a app orquestre containers ou VMs. O MySQL é provisionado automaticamente, com
volume, readiness e seed. E a persistência guarda os dados em volume e os perfis
cifrados por uma passphrase mestra. Cada tema resolve uma parte de um problema
real de entrega de software — e sempre com um trade-off consciente entre
praticidade e segurança. Obrigado! Ficamos à disposição para perguntas.

---

## Slide 16 — Demo & comandos (backup)

> `APÊNDICE` — "Se quiser ver rodando"
> ⏱️ ~0:30 · use só se sobrar tempo ou se perguntarem "como eu rodaria isso?"

Se quiserem ver rodando, o fluxo é bem direto. Um `make build` compila os módulos
e roda os testes; um `make up` sobe a aplicação com o noVNC e um MySQL de teste, e
aí é só abrir o `localhost:6080/vnc.html` no navegador; o `make it` roda o teste de
integração, que provisiona um MySQL real, aplica o seed, lê e limpa; e o
`make down` derruba a stack. Na interface, o fluxo é: criar uma conexão nova,
testar, conectar, escrever o SQL e executar com F5 — e, para provisionar do zero,
é o botão "Novo ambiente MySQL".
