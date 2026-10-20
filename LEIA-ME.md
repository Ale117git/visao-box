# Visão Box — sistema de realidade mista para VR box

Um sistema inspirado no visionOS, feito para um celular Android dentro de um VR box comum.

## O que tem

- **Visão binocular**: uma imagem para cada olho, com paralaxe de verdade (elementos próximos e distantes têm profundidade diferente), correção de distorção da lente e centros dos olhos posicionados pela IPD em milímetros reais.
- **Dock flutuante** com relógio e os apps Navegador, Galeria, Câmera, Ajustes, Arquivos, Web e Menu. No modo espacial ele fica **fixo no ambiente**: quando você vira a cabeça, ele fica no lugar (como uma janela), e uma seta na borda mostra onde ele está. Volume − (ou Ajustes → Recentralizar) traz o dock para a sua frente. Para mover, faça a pinça na barrinha embaixo dele e arraste. Para ele seguir a cabeça, ligue a opção em Ajustes → Dock.
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
  - ponteiro no centro da visão;
  - "tocar olhando parado";
  - toque na tela;
  - mouse;
  - controle ou teclado Bluetooth;
  - modo simplificado com alvos grandes.
- **Painel de ajustes**, aberto pela engrenagem. Todas as mudanças aparecem na hora. O painel também tem a **tela de calibração**, os perfis (salvar e carregar) e a opção de restaurar os valores padrão.

## Controle Bluetooth

| Botão | Ação |
|---|---|
| A / Enter / Volume + | toca no ponteiro central |
| Setas | movem o ponteiro |
| B / Esc | fecha a janela da frente |
| Volume − | recentraliza a visão |

## Desempenho (motor OpenGL ES)

- A imagem da câmera chega já em pé (o próprio CameraX gira) e é enviada à placa de vídeo reaproveitando a memória.
- A interface é desenhada **uma vez por quadro** com Canvas acelerado por hardware; o 3D de cada olho é feito no shader, deslocando a imagem.
- Um shader GLSL junta câmera, recorte de pessoas, interface, cores e vinheta numa passada só.
- A distorção da lente é feita por uma malha de vértices.
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
| `MainActivity.java` | navegador, galeria, arquivos, permissões, teclado |

## Gerar o APK

Pela aba **Actions** do GitHub, na tarefa **Gerar APK**. Os modelos de mãos e de recorte de pessoas são baixados automaticamente durante a compilação e ficam dentro do app.

## Limitações do hardware

- O giroscópio mede só a **rotação** da cabeça, não a **posição**. Andar pela sala não move as janelas.
- O recorte de pessoas separa a silhueta da imagem, mas **não mede a distância real**. O efeito de "janela atrás da pessoa" é uma aproximação visual.
- Muitos VR box tapam a câmera traseira. Sem imagem da câmera, o app não simula o ambiente: use o fundo Virtual 3D ou Escuro.
- Os ajustes digitais não mudam a distância física entre as lentes, o foco nem a qualidade delas.
- No navegador, vídeos podem não aparecer dentro da janela (o Android desenha vídeo numa camada separada). Páginas, textos e imagens funcionam.
