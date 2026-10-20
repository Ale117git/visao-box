# Visão Box — app Android nativo

Interface no estilo Vision Pro para usar o celular dentro de um VR box.
A câmera mostra o ambiente e o app reconhece as suas mãos (MediaPipe),
tudo dentro do app, sem internet e sem página web.

## Como usar
- Junte polegar e indicador (pinça) em cima de um ícone para abrir.
- Faça a pinça na barrinha embaixo de uma janela para arrastar; o "x" fecha.
- O botão redondo embaixo abre e fecha o início.
- A engrenagem no canto abre os ajustes: modo VR / tela única, separação dos olhos,
  zoom, efeito 3D e troca de câmera.
- Sem VR box, também dá para tocar na tela com o dedo.

## Gerar o APK
O GitHub gera sozinho pela aba Actions (tarefa "Gerar APK").
Durante a compilação, o modelo de reconhecimento de mãos é baixado e colocado dentro do app.

Código principal:
- app/src/main/java/br/visaobox/app/MainActivity.java (câmera, modelo, ajustes)
- app/src/main/java/br/visaobox/app/VrView.java (interface, gestos, apps)
