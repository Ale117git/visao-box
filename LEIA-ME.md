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
- Navegador e YouTube (Android 10+): a página é desenhada pela placa de vídeo direto num buffer, em resolução cheia e com os vídeos. Em aparelhos mais antigos, ou com a opção desligada em Ajustes, a cópia é por software.
- O reconhecimento de mãos usa a GPU quando dá (com reserva no processador), acompanha uma mão e roda até ~20 vezes por segundo.
- A análise da câmera reaproveita a memória a cada quadro, sem pausas para limpeza de memória.

## Módulos (app/src/main/java/br/visaobox/app)

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
| `WebPane.java` | uma página (aba, YouTube ou Player): captura, rolagem, modo computador e arquivos de mídia |

## Gerar o APK

Pela aba **Actions** do GitHub, na tarefa **Gerar APK**. Os modelos de mãos e de recorte de pessoas são baixados automaticamente durante a compilação e ficam dentro do app.

## Limitações do hardware

- O giroscópio mede só a **rotação** da cabeça, não a **posição**. Andar pela sala não move as janelas.
- O recorte de pessoas separa a silhueta da imagem, mas **não mede a distância real**. O efeito de "janela atrás da pessoa" é uma aproximação visual.
- Muitos VR box tapam a câmera traseira. Sem imagem da câmera, o app não simula o ambiente: use o fundo Virtual 3D ou Escuro.
- Os ajustes digitais não mudam a distância física entre as lentes, o foco nem a qualidade delas.
- A gravação pega o som do microfone, não o som dos vídeos do YouTube (isso exigiria permissão de gravar a tela inteira do celular).
- Em Android 9 ou mais antigo (ou no modo compatível do navegador), vídeos podem ficar pretos dentro da janela. Páginas, textos e imagens funcionam.
