# Visão Box — app Android

App Android que abre o Visão Box em tela cheia, com a câmera liberada
(não precisa de site https). A interface fica em `app/src/main/assets/index.html`.

## Jeito 1 — GitHub (sem instalar nada no computador)

1. Crie um repositório novo no GitHub (pode ser privado).
2. Envie todos os arquivos desta pasta para ele (botão **Add file → Upload files**).
   A pasta `.github` também precisa ir.
3. Abra a aba **Actions**. A tarefa **Gerar APK** roda sozinha (leva uns 3–5 minutos).
4. Quando ficar verde, clique nela e baixe **VisaoBox-apk** em *Artifacts*.
5. Descompacte o .zip, passe o `app-debug.apk` para o celular e instale.
   O Android vai pedir para permitir "instalar apps desconhecidos".

## Jeito 2 — Android Studio

1. Abra o Android Studio → **Open** → escolha esta pasta.
2. Espere o Gradle sincronizar.
3. Ligue o celular no cabo com a depuração USB ativa e clique em **Run ▶**,
   ou use **Build → Build APK(s)**.

## Observações

- Na primeira vez o app precisa de internet para baixar o reconhecimento de mãos.
- O app abre deitado (paisagem), para encaixar no VR box.
- Para mudar a interface, edite `app/src/main/assets/index.html` e gere o APK de novo.
