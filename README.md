# RTSP Viewer

App Android ligera y a pantalla completa para reproducir el flujo de una cámara RTSP,
pensada para instalarse en teléfonos Android antiguos.

## Características

- Usa **ExoPlayer 2.19.1** (versión clásica, no Media3) con su extensión RTSP.
  No depende de librerías nativas pesadas (nada de VLC/FFmpeg completo), por lo
  que el APK final pesa pocos MB.
- Sin AppCompat/Material: arranca más rápido y consume menos RAM en hardware viejo.
- Pantalla completa inmersiva (oculta barra de estado y de navegación).
- Pantalla siempre encendida mientras la app está en primer plano.
- Reconexión automática cada 5 segundos si se pierde la señal.
- Fuerza RTSP sobre TCP (`setForceUseRtpTcp(true)`), más estable en WiFi doméstico
  que UDP.
- La URL de la cámara se guarda en el dispositivo; para cambiarla, **mantén
  pulsada la pantalla**.

## Cómo compilar

### Opción 0: GitHub Actions (compilar en la nube, sin instalar nada)
1. Crea un repositorio nuevo en GitHub (puede ser privado).
2. Sube todo el contenido de esta carpeta (incluida la carpeta oculta
   `.github/`) a ese repositorio — por la web ("Add file → Upload files",
   arrastrando la carpeta) o con `git push`.
3. Ve a la pestaña **Actions** del repositorio. En cuanto detecte el push
   a `main`/`master`, se lanzará el workflow "Build APK" automáticamente
   (tarda 2-4 minutos).
4. Cuando termine (✅ verde), entra en esa ejecución y baja hasta
   **Artifacts** → descarga `RTSPViewer-debug-apk`. Es un .zip que contiene
   `app-debug.apk`, ya firmado con la firma de depuración y listo para
   instalar directamente en el teléfono (actívalo tocando el archivo, con
   "orígenes desconocidos" permitido).
5. Si no se lanza solo, puedes forzarlo manualmente desde Actions →
   "Build APK" → "Run workflow".

### Opción A: Android Studio (recomendado si tienes PC a mano)
1. Abre Android Studio → "Open" → selecciona la carpeta `RTSPViewer`.
2. Deja que Gradle sincronice (descargará ExoPlayer automáticamente).
3. Conecta el teléfono por USB con la depuración USB activada y pulsa "Run".

### Opción B: línea de comandos
```bash
cd RTSPViewer
./gradlew assembleRelease
```
El APK queda en `app/build/outputs/apk/release/app-release-unsigned.apk`.
Para instalarlo necesitas firmarlo (o usa `assembleDebug` para probar rápido
sin firma, generando `app-debug.apk`, instalable directamente con
`adb install app-debug.apk`).

## Ajustar la compatibilidad con teléfonos muy antiguos

En `app/build.gradle`, el `minSdk` está puesto en **19** (Android 4.4, KitKat).
Si tu teléfono es aún más viejo (Android 4.1–4.3, Jelly Bean), puedes bajarlo
hasta `minSdk 16`, que es el mínimo soportado por ExoPlayer 2.19.x:

```gradle
defaultConfig {
    minSdk 16
    ...
}
```

## Primer uso

La URL `rtsp://192.168.1.146:8554/mirilla` ya viene precargada por defecto,
así que la app conectará sola al abrirla la primera vez. Si necesitas
cambiarla más adelante (otra red, otra IP), **mantén pulsada la pantalla**
mientras la app está abierta y edítala ahí; quedará guardada para las
próximas veces.

## Notas de rendimiento en hardware antiguo

- Si el stream de la cámara es en H.265/HEVC y el teléfono es muy antiguo,
  puede no tener decodificador por hardware; en ese caso pide a la cámara
  (en su configuración web) que emita en **H.264**, mucho más compatible.
- Baja la resolución/bitrate del "sub-stream" de la cámara si notas
  tirones — casi todas las cámaras IP ofrecen un segundo perfil de vídeo
  más ligero pensado justo para esto.
