# D'Mais Radar B2B Android

Aplicativo Android nativo (WebView) para o Radar B2B da D'Mais.

## Versão
- App: 1.0.2
- Package: `br.com.dmaismkt.radarb2b`
- URL carregada: `https://dmaismkt.com.br/radar-b2b/?radar_app=android`

## Recursos nativos
- localização
- câmera / QR Code
- microfone
- upload de arquivos
- cookies e sessão do Radar
- abertura de links externos fora do app
- bridge de compartilhamento

## Build automático
O workflow `.github/workflows/build-apk.yml` gera um APK debug instalável a cada push em `main` e também pode ser executado manualmente.

Artefato esperado: `app/build/outputs/apk/debug/app-debug.apk`.
