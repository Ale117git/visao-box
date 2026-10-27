# Visão Box — sistema de realidade mista para VR box

Um sistema inspirado no visionOS, feito para um celular Android dentro de um VR box comum.

## O que tem

- **Visão binocular**: uma imagem para cada olho, com paralaxe de verdade (elementos próximos e distantes têm profundidade diferente), correção de distorção da lente e centros dos olhos posicionados pela IPD em milímetros reais.
- **Dock flutuante** com relógio e os apps Navegador, Galeria, Câmera, Ajustes, Arquivos, Web e Menu. No modo espacial ele fica **fixo no ambiente**: quando você vira a cabeça, ele fica no lugar (como uma janela), e uma seta na borda mostra onde ele está. Volume − (ou Ajustes → Recentralizar) traz o dock para a sua frente. Para mover, faça a pinça na barrinha embaixo dele e arraste. Para ele seguir a cabeça, ligue a opção em Ajustes → Dock.
- **Barra de rolagem** ao lado do Navegador e do YouTube: arraste a barrinha (pinça, A ou toque) para rolar a página, como no Chrome; clicar no trilho pula uma tela.
- **YouTube** como app próprio, com pesquisa pelo teclado virtual, vídeos tocando dentro da janela e botão de **modo cinema** (tela grande). O Navegador também tem esse botão.
- **Gravar a realidade mista**: no app Câmera, o botão vermelho grava um vídeo em tela cheia (sem dividir os olhos), com a câmera de fundo, as janelas por cima e o som do microfone. O vídeo vai para a galeria do celular, em Filmes › Visão Box, e para o app Arquivos.
- **Teclado virtual flutuante e móvel**, com letras, números, símbolos e acentos. Ele digita nas páginas (abre sozinho ao tocar num campo de texto), na barra de endereço ou pesquisa do Navegador e no app Notas.
- **Janelas flutuantes** de vidro fosco, com barra de título e controles para fechar, minimizar, aproximar, afastar e pôr atrás ou na frente da pessoa. Dá para mover pela barrinha ou pelo título e redimensionar pelo canto. Várias janelas podem ficar abertas ao mesmo tempo.
- **Três modos**:
  - HUD: as janelas ficam presas à visão.
  - Espacial: as janelas ficam fixas no ambiente e você vira a cabeça para vê-las.
  - Cinema: uma tela grande a 3 m de distância.
- **Rastreamento rotacional da cabeça** pelo giroscópio, com suavização, sensibilidade e recentralização.
- **Fundos**:
  - Câmera (passthrough), com brilho, contraste, opacidade e zoom.
  - Ambiente virtual 3D, com céu e piso em perspectiva.
  - Escuro.
- **Recorte de pessoas** (segmentação em tempo real), nos modos Recorte, Silhueta ou Contorno. As janelas marcadas com ◐ aparecem atrás da pessoa.
- **Interação**:
  - pinça com a mão na frente da câmera;
  - páginas: pinça (ou A) rápida = clique; pinça e arrastar = rolar;
  - ponteiro no centro da visão;
  - "tocar olhando parado";
  - toque na tela;
  - mouse;
  - controle ou teclado Bluetooth;
  - modo simplificado com alvos grandes.
- **Painel de ajustes**, aberto pela engrenagem. Todas as mudanças aparecem na hora. O painel também tem a **tela de calibração**, os perfis (salvar e carregar) e a opção de restaurar os valores padrão.

## Correção 6.5.1
- O modo controle fechava ao abrir no Android 12 ou mais novo (o sensor era pedido rápido demais, acima do limite de 200 leituras por segundo sem permissão especial). Corrigido.
- Se o app fechar com erro, na próxima abertura aparece o texto do erro com o botão "Copiar".
- Se o modo controle fechar ao abrir, o app não fica mais preso abrindo direto nele.

## Novidades da 6.5 — mouse no ar, touchpad e Bluetooth
- **Ligação por Bluetooth (sem Wi-Fi):** o celular-controle vira um controle de videogame de verdade (HID). No controle: Bluetooth → Conectar → "Ficar visível". No celular do óculos: Configurações → Bluetooth → parear o celular-controle. Depois conecta sozinho. Precisa de Android 9+ e de um fabricante que não bloqueie o recurso; se bloquear, o app avisa e o Wi-Fi continua funcionando.
- **Mouse no ar (novo padrão):** girar o celular move a mira, como o controle de uma Smart TV. Não tem "frente" para alinhar, então não escorrega. Tremor pequeno é ignorado; movimento rápido acelera. Funciona segurando deitado ou em pé.
- **Touchpad:** o dedo move a mira, como no notebook. Toque = clicar, dois dedos = rolar. É o modo mais estável.
- **Raio:** o modo da 6.3/6.4 continua disponível (só pelo Wi-Fi).
- Troque o modo no botão "Modo: …" no topo da tela do controle. ◎: toque = mira no centro, segurar = centralizar tudo.
- Sensibilidade do mouse no ar: Baixa / Média / Alta, na tela ⚙ do controle.

## Novidades da 6.4 — controle sem atraso, tremor e desvio
- **Menos atraso:** os dois celulares desligam a economia de energia do Wi-Fi enquanto o controle está ligado (ela segurava os pacotes e o raio andava aos pulos). O controle manda a cada leitura do sensor (até ~120 por segundo) e o óculos prevê alguns milissegundos à frente para compensar a rede.
- **Sem tremor:** filtro "One Euro" na mira — firme com a mão parada, rápido com a mão em movimento.
- **Sem escorregar:** bússola corrigindo devagar o giroscópio nos dois celulares (Ajustes → Modo → "Bússola contra o desvio"; no controle, na tela de configuração). Desligue se o óculos tiver ímã.
- **Segure como quiser:** deitado (como controle de TV) ou em pé (como câmera). Ao tocar ◎, o app aprende o jeito que você segura. Antes, segurar em pé deixava a conta instável.
- Dica: o hotspot do celular do óculos costuma dar menos atraso que o Wi-Fi de casa.

## Novidades da 6.3 — outro celular como controle
- **Modo controle:** instale o mesmo APK no segundo celular, abra e toque em "Este celular É o controle" (ou Ajustes → Modo → "Virar controle"). Da próxima vez ele já abre como controle; para voltar, toque em ⚙ → "Usar este celular como óculos".
- **Parear:** os dois no mesmo Wi-Fi (ou o celular do óculos com o roteador/hotspot ligado e o controle conectado nele). A tela de início do óculos mostra o código de 4 dígitos e o IP; digite o código no controle. O IP só é preciso se a busca automática falhar.
- **Como segurar:** em pé, tela para cima, topo apontando para a frente. O raio sai da sua mão (modelo de braço) e a mira fica onde ele cruza as janelas.
- **Botões:** toque no touchpad = clicar · arrastar no touchpad = rolar · A = clicar (segurar arrasta pela barra) · B = voltar · ✊ = segurar e arrastar qualquer janela · ▦ menu · ⌨ teclado · 🎤 voz · ◎ toque = alinhar o raio com o seu olhar, segure = centralizar tudo · Volume + = clicar · Volume − = segurar janela.
- O controle vibra ao passar o raio por um botão. Rede: UDP na porta 47811 (~90 pacotes por segundo).
- Limite: é rotação (3 graus de liberdade). A posição da mão é estimada, não medida; se o raio "escorregar" com o tempo, toque em ◎.

## Novidades da 6.2

- **Mãos por cima das janelas**, em Ajustes → Modo → Aparência das mãos:
  - **Luva:** a mão vira uma luva escura com detalhes azuis.
  - **Mão real:** recorta a sua mão da imagem da câmera.
  - **Esqueleto:** como era antes.
  
  Funciona com o rastreamento das mãos ligado.
- **Puxar a janela com a mão.** Segure a janela pela barra com a pinça e traga a mão para perto do rosto: a janela vem junto e fica maior. Empurre para longe e ela se afasta e fica menor.
- **Páginas sem "zoom".** A página agora é montada com largura de tablet (antes era a de um celular pequeno), então dá para ver muito mais coisa no YouTube e nos sites. Em Ajustes → Conforto → Tamanho das páginas há quatro opções: Grande, Médio, Normal e Pequeno.
- **Apps do celular** (app novo): lista os apps instalados e abre qualquer um, como Minecraft ou YouTube. Também funciona por voz: "abrir Minecraft". O app abre na tela normal do celular, fora do óculos, porque o Android não deixa um app mostrar outro dentro dele. Para voltar, abra o Visão Box de novo.

## Novidades da 6.1

- **Formato e resolução da imagem**, na tela de início e em Ajustes → Visão:
  - Formato de cada olho: 1:1 (quadrado), 4:3, 3:4, 16:9, 9:16 ou tela cheia.
  - Resolução: automática (a da tela, compensando a lente), 480p, 720p, 1080p ou 1440p. Resolução menor deixa o app mais leve; maior deixa mais nítido.

## Novidades da 6.0

- **Navegador novo.** Cada página (abas, YouTube, Player) agora roda numa *tela virtual* de verdade do Android, do tamanho exato da janela. O navegador desenha tudo normalmente, então a rolagem não deixa mais partes brancas e não trava o app. As letras das páginas podem ser aumentadas em Ajustes → Conforto.
- **Tela de início.** Antes de ligar o VR, você escolhe **Controle** (mais leve) ou **Mãos** (mais imersivo) e acerta os ajustes principais. A câmera e o reconhecimento de mãos só ligam depois de "Começar", por isso não trava enquanto você configura. No controle: o direcional anda, A escolhe e Start começa.
- **Todos os ajustes dentro do óculos.** O app Ajustes tem agora as mesmas opções da engrenagem, separadas em abas: Modo, Visão, Conforto, Fundo, Janelas, Assistente e Sistema. A calibração também funciona no óculos: no controle, ← → muda a distância dos olhos, ↑ ↓ a distorção, e A ou B sai.
- **Duas mãos.** Cada mão tem a sua pinça. Faça a pinça com as duas mãos na mesma janela e afaste-as para **aumentar** a janela, ou aproxime-as para diminuir.
- **Fundos** (app novo): câmera, ambientes, 5 paisagens 360° prontas (Aurora, Rio na floresta, Cidade à noite, Montanhas e Fundo do mar) ou **uma foto sua** da galeria.
- **Clima** (app novo): chuva, tempestade com raios e trovão, neve, neblina, vaga-lumes, estrelas cadentes e folhas caindo, com intensidade e som. Também funciona por voz: "fazer chover", "tempestade", "neve", "tirar o clima".
- **Pingue-pongue** (jogo novo) contra o computador. A raquete segue a mão, a mira ou o dedo.
- **Visão ou Jarvis.** Em Ajustes → Assistente, o nome pode ser Visão ou Jarvis. Com Jarvis, a chamada é "ei Jarvis" e o ícone muda. Como "Jarvis" não é palavra do português, a chamada dele pode errar um pouco mais que "ei Visão".

## Novidades da 5.1

- **Ícone novo** do app.
- **Visão, o assistente pessoal.** Diga **"ei visão"** e depois o pedido. Ele responde falando, com voz grave e calma. O tom e a velocidade da voz se ajustam em Ajustes → Assistente Visão, e lá também dá para trocar de voz.
  - Ele faz tudo do comando de voz: abrir apps, pesquisar, tocar músicas, gravar, tirar foto, modo cinema e fechar janelas.
  - Ele também responde sozinho: "que horas são", "que dia é hoje", "bateria", "quanto é 7 vezes 8", "conta uma piada", "me chame de (seu nome)", "pare de falar".
  - Para perguntas como "o que é…" ou "quem foi…", ele pesquisa na internet.
  - O "ei visão" é reconhecido **sem internet**, por um modelo de voz em português que vem dentro do app (por isso o APK ficou maior). Dá para desligar em Ajustes. Enquanto o app grava vídeo, ele para de ouvir.
  - Para ter mais vozes: Configurações do Android → Saída de texto para voz → instale as vozes em português do Google.

## Novidades da 5.0

- **Navegador com abas** (até 8), **★ favoritos**, **histórico**, **página inicial** com favoritos e recentes, botão de casa e **modo computador** (ícone de monitor nas abas).
- **Apps web**: WhatsApp Web, Instagram e X no app Web. O WhatsApp abre sozinho em modo computador. Espelhar os apps instalados no celular não é possível: o Android não deixa um app mostrar a tela de outro.
- **Mídia**: vídeos e músicas do celular, tocando na janela **Player** (com modo cinema e avançar/voltar).
- **Ambientes 360°**: Espaço, Praia, Pôr do sol e Sala de cinema (Ajustes → Fundo).
- **Comando de voz** (ícone de microfone no dock, ou **R3** no controle). Exemplos: "abrir YouTube", "pesquisar receita de bolo", "tocar Coldplay", "fechar", "fechar tudo", "recentralizar", "gravar", "parar", "tirar foto", "modo cinema", "teclado". Com o teclado aberto, o que você fala é digitado.
- **Lembrar as janelas**: ao reabrir o app, as janelas, as abas e as páginas voltam onde estavam.
- **Nave**: jogo de nave. Analógico esquerdo (ou a mira) move, **A** ou pinça atira.
- **Janelas presas a QR codes**: aponte a câmera para um QR code e aperte **📌** na barra da janela. A janela gruda no QR e acompanha o objeto real. Aperte de novo para soltar.

## Controle de Xbox / Bluetooth (recomendado)

A mira fica no centro da visão: você aponta com a cabeça e confirma no controle. É bem mais leve que o rastreamento das mãos, que vem **desligado** e pode ser ligado em Ajustes.

| Botão | Ação |
|---|---|
| A / RT | confirmar na mira (segurar A na barra da janela = arrastar) |
| B | voltar: cancela o arraste, fecha o teclado, sai da foto ou fecha a janela |
| X | menu de apps |
| Y | teclado (barra do navegador ou Notas) |
| LB / RB | afastar / aproximar a janela da mira |
| LT (segurar) | arrastar qualquer janela com a mira |
| ≡ Start | Ajustes |
| ⧉ View / Volume − | recentralizar a visão |
| Analógico esquerdo | ajustar a mira (L3 volta ao centro) |
| Analógico direito | rolar a página (como um dedo arrastando) / trocar foto |
| Setinhas ↑ ↓ | rolar a página aos pedaços (segurar continua) |
| Setinhas ← → | mover a mira |
| R3 (apertar o analógico direito) | comando de voz |

O app **Controle** (no menu) mostra o controle ao vivo, com os botões acendendo, para testar.

## Desempenho (motor OpenGL ES)

- A imagem da câmera chega já em pé (o próprio CameraX gira) e é enviada à placa de vídeo reaproveitando a memória.
- A interface é desenhada **uma vez por quadro** com Canvas acelerado por hardware; o 3D de cada olho é feito no shader, deslocando a imagem.
- Um shader GLSL junta câmera, recorte de pessoas, interface, cores e vinheta numa passada só.
- A distorção da lente é feita por uma malha de vértices. A interface é desenhada com sobra de resolução para compensar o zoom da lente, e por isso fica nítida.
- Navegador, YouTube e Player: cada página roda numa tela virtual. No Android 10+, os quadros chegam pela placa de vídeo sem cópia; nos mais antigos, são copiados ~15 vezes por segundo. Página parada não gasta nada.
- O reconhecimento de mãos usa a GPU quando dá (com reserva no processador), acompanha uma mão e roda até ~20 vezes por segundo.
- A análise da câmera reaproveita a memória a cada quadro, sem pausas para limpeza de memória.

## Módulos (app/src/main/java/br/visaobox/app)
- `ControllerActivity` — modo controle (tela de botões, sensores, envio pela rede)
- `RemoteLink` / `RemoteProto` — recepção do celular-controle no óculos e o protocolo UDP
- `YawFusion` / `OneEuro` — bússola contra o desvio e filtro de tremor
- `BtGamepad` — o celular-controle como controle Bluetooth (HID)

| Arquivo | Função |
|---|---|
| `VrView.java` | motor OpenGL ES: shaders, malha de distorção, câmera, recorte, calibração |
| `SpatialUi.java` | janelas, dock, apps e interação |
| `HeadTracker.java` | sensores e rastreamento da cabeça |
| `CameraVision.java` | captura da câmera, mãos e segmentação (MediaPipe) |
| `SettingsPanel.java` | ajustes ópticos e de conforto |
| `Config.java` | configurações e perfis |
| `Gfx.java` / `Synth.java` | desenho e sons |
| `MainActivity.java` | galeria, arquivos, permissões, teclado, controle |
| `WebPane.java` | uma página (aba, YouTube ou Player) numa tela virtual: quadros, rolagem, modo computador e arquivos de mídia |
| `SettingsModel.java` | lista única de ajustes (engrenagem, tela de início e app Ajustes) |
| `Backdrop.java` / `Weather.java` | fundo em imagem 360° e efeitos de clima com som |
| `Assistant.java` | assistente Visão / Jarvis: chamada por voz, fala e respostas |

## Gerar o APK

Pela aba **Actions** do GitHub, na tarefa **Gerar APK**. Os modelos de mãos e de recorte de pessoas são baixados automaticamente durante a compilação e ficam dentro do app.

## Limitações do hardware

- O giroscópio mede só a **rotação** da cabeça, não a **posição**. Andar pela sala não move as janelas.
- O recorte de pessoas separa a silhueta da imagem, mas **não mede a distância real**. O efeito de "janela atrás da pessoa" é uma aproximação visual.
- Muitos VR box tapam a câmera traseira. Sem imagem da câmera, o app não simula o ambiente: use o fundo Virtual 3D ou Escuro.
- Os ajustes digitais não mudam a distância física entre as lentes, o foco nem a qualidade delas.
- A gravação pega o som do microfone, não o som dos vídeos do YouTube (isso exigiria permissão de gravar a tela inteira do celular).
- Em Android 9 ou mais antigo (ou no modo compatível do navegador), vídeos podem ficar pretos dentro da janela. Páginas, textos e imagens funcionam.
