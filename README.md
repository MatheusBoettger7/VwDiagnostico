# VwDiagnostico

Aplicativo Android para diagnóstico automotivo com foco inicial em comunicação com adaptadores OBD-II Bluetooth Classic, incluindo o **Vgate iCar Pro BT3.0**.

## Objetivo da primeira versão

Esta primeira etapa é deliberadamente somente leitura:

- conectar a um dispositivo Bluetooth Classic já pareado;
- usar RFCOMM/SPP;
- conversar com o adaptador através de comandos ELM327;
- exibir comunicação TX/RX em tempo real;
- permitir testes básicos como `ATI`;
- preparar a base para as próximas camadas: CAN, ISO-TP, UDS e protocolos VAG.

Ainda **não** existem funções de apagamento de falhas, codificação, adaptação ou escrita em módulos do veículo.

## Como testar

1. Pareie o Vgate iCar Pro BT3.0 nas configurações de Bluetooth do Android.
2. Instale o APK de debug gerado pelo GitHub Actions.
3. Abra o aplicativo e conceda a permissão de **Dispositivos por perto**.
4. Selecione o Vgate na lista.
5. Toque em **Conectar**.
6. Com a conexão ativa, toque em **Teste ATI** ou em **Inicializar ELM327**.
7. Observe o painel **Comunicação RAW**.

A resposta esperada para `ATI` normalmente identifica o firmware do adaptador. O objetivo desta etapa é confirmar a camada Android -> Bluetooth -> ELM327 antes de tentar conversar com os módulos do Virtus.

## Build

O projeto usa Android Gradle Plugin 9.2.0, Gradle 9.4.1 e JDK 17.

Para gerar o APK localmente:

`gradle :app:assembleDebug`

O workflow `.github/workflows/android-debug.yml` também gera automaticamente o APK de debug em cada push na branch `main`.
