# Advanced VR Interface (Android / Kotlin)

Sistema VR estilo Meta Quest para celular em headset (Cardboard-like): janelas curvas flutuantes, dock, navegador real (WebView),
teclado virtual, galeria, arquivos, apps instalados, notificações, passthrough da câmera. **SEM rastreamento de mãos.**

## Como abrir
1. Android Studio (Koala+) -> Open -> pasta `AdvancedVR`. Aguarde o sync do Gradle (wrapper 8.7, AGP 8.5.2, Kotlin 1.9.24).
2. Rode em um celular real (minSdk 28). Emulador não tem giroscópio/lentes.
3. Build > Build APK(s).

## Controles (sem tracking)
- **Modo VR (padrão):** o cursor é o olhar (centro de cada olho). **Tocar a tela = pinça** (clicar / segurar / arrastar / rolar).
- **Mão virtual:** desenhada SEMPRE por cima das janelas, na posição do cursor; fecha em pinça ao tocar.
- **Mover janela:** toque e segure a barra branca sob a janela e vire a cabeça; arraste o dedo para cima/baixo para afastar/aproximar (profundidade real).
- **Redimensionar:** segure os cantos inferiores (aparecem ao mirar). **Maximizar/minimizar/fechar/fixar à visão:** barra de título.
- **Atalhos (configuráveis em Configurações):** Volume+ = menu/dock, Volume− = voltar, toque duplo no vazio = teclado, toque longo no vazio = configurações.
- **Modo tela (desligue "Modo VR estéreo"):** tela única; o dedo é o cursor.

## Limitações honestas
- Não foi compilado no ambiente de geração (sem Android SDK/rede). Pode haver erros de compilação pontuais a corrigir no Android Studio.
- Apps Android de terceiros não rodam dentro de janelas VR (exige privilégios de sistema); são lançados em tela cheia (sai do VR; Voltar retorna).
- Rastreamento de cabeça é 3DoF (giroscópio), sem ARCore; não há posição no espaço real.
- Passthrough usa a câmera traseira com zoom de preenchimento (não é estéreo real).
- Os conteúdos web são capturados por software (WebView); pode custar FPS em celulares fracos (reduza "Resolução de renderização").
- Referências: barra branca ficou ABAIXO da janela (como nas imagens); há opção para colocá-la em cima.
