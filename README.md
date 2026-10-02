# MTV — Reproductor IPTV

App Android (100% Kotlin, un solo módulo `:app`, un solo APK) para celular y Android TV.

## Funciones

- **Login Xtream Codes** contra servidores del usuario (3 precargados: Lion TV, TVPrem, CC IPTV).
  Si el esquema original falla por red/timeout/SSL, reintenta automáticamente con el esquema
  alterno (https ↔ http) antes de mostrar error.
- **En vivo / Películas / Series**: categorías, buscador por texto, grids y detalle.
- **Metadata TMDB** (sinopsis, reparto, similares, trailers) con limpieza de títulos Xtream.
- **Reproductor Media3 (ExoPlayer)**: HLS/DASH/TS, continuar donde quedó, velocidad 0.5–2x,
  ajuste de pantalla, pistas de audio/subtítulos, PiP y bloqueo de controles en celular;
  gestos (brillo/volumen/seek/doble-tap) en celular y controlador D-pad en TV.
- **Seguir viendo** y **Favoritos** (Room). Sin EPG.

## Compilar

```bash
./gradlew assembleDebug
```

Requiere Android SDK (compileSdk 34). La TMDB API key va compilada como
`BuildConfig.TMDB_API_KEY` (inyectada en `app/build.gradle.kts`).

## Notas

- `android:usesCleartextTraffic="true"`: los servidores Xtream usan HTTP.
- El icono actual es un placeholder temporal ("MTV") hasta recibir el logo oficial.
