# Arquitectura — MTV IPTV

## Base
- **Package**: `com.mtv.iptv`
- **Lenguaje**: 100% Kotlin, un solo módulo `:app`, un solo APK para celular y Android TV.
- **UI**: Jetpack Compose (móvil) + Compose para TV (navegación 100% D-pad en TV).
- **Reproductor**: Media3 (ExoPlayer) 1.9.0 — HLS/DASH/TS.
- **Red**: Xtream Codes API (login con usuario/clave contra servidores del usuario).
- **Metadata**: TMDB (sinopsis, reparto, similares, trailers; key compilada como `BuildConfig`).
- **Persistencia**: Room (favoritos, seguir viendo, descargas) + DataStore/EncryptedSharedPreferences (sesión, prefs).
- **Segundo plano**: WorkManager (actualización periódica del catálogo).

## Archivos clave
| Área | Archivo |
|---|---|
| Interfaz TV (riel, grupos, grillas, detalle) | `app/src/main/java/com/mtv/iptv/ui/tv/TvApp.kt` |
| Overlay del reproductor TV (HUD, controles D-pad) | `app/src/main/java/com/mtv/iptv/ui/tv/TvPlayerOverlay.kt` |
| Actividad del reproductor | `app/src/main/java/com/mtv/iptv/iptv/PlayerActivity.kt` |
| Recuperación de reproducción (reintentos, re-login) | `app/src/main/java/com/mtv/iptv/player/PlaybackRecovery.kt` |
| Preferencias de usuario | `app/src/main/java/com/mtv/iptv/data/prefs/UserPrefs.kt` |
| Repositorio Xtream (catálogo con caché en disco) | `app/src/main/java/com/mtv/iptv/data/repository/XtreamRepository.kt` |
| Navegación móvil | `app/src/main/java/com/mtv/iptv/ui/mobile/MobileNav.kt` |
| Ajustes TV | `app/src/main/java/com/mtv/iptv/ui/tv/SettingsRootScreen.kt` (+ `GeneralScreen`, `RemoteScreen`, `ParentalScreen`) |
| Test de velocidad | `app/src/main/java/com/mtv/iptv/.../SpeedTest.kt` |
| FFmpeg LGPL (respaldo de audio) | módulo `decoder_ffmpeg/` |

## Decisiones fijas
- **Sin EPG real ni guía de programación** (decisión del dueño; las celdas dicen "Sin información").
- **Sin VLC embebido**: reproductor externo solo por Intent.
- **FFmpeg LGPL** como respaldo de audio (AC3/DTS), compilado solo para ARM.
- **Media3 1.9.0** (no usar APIs fuera de su superficie pública).
- **Keystore debug fijo** en el repo (`debug.keystore`): todas las versiones comparten firma y se actualizan sin desinstalar.
- **6 pósters por fila** en TV; iconos + color distintivo por categoría en Ajustes de TV.
- `android:usesCleartextTraffic="true"`: los servidores Xtream usan HTTP.
- Al corregir: **reemplazar el código malo, no apilar código encima** (regla del dueño).

## Builds y releases
- GitHub Actions compila el APK debug en cada push a `main`.
- Los tags `v*` publican un release con el asset fijo `mtv-latest.apk`.
- Enlace estable para descarga: `https://github.com/josueluz89/mtv/releases/latest/download/mtv-latest.apk`
