# Changelog — MTV IPTV

Historial de versiones (todas del 2026-10-02 salvo que se indique otra fecha).

## v1.9.2
- HUD del reproductor estilo Masters TV con info completa del stream (calidad, resolución, FPS, códecs, bitrate).
- Ordenación en series corregida (el API manda `last_modified`, no `added`).
- Carpetas 20% más pequeñas en TV.
- VOD confiable: reintentos automáticos, re-login silencioso en 401/403, overlay "Reconectando…" con Reintentar/Cerrar.
- TV en vivo: carpetas "Todo" y "★ Favoritos".
- Películas y series: carpeta "Todo" con conteo.

## v1.9.1
- Reproductor TV reescrito: opciones debajo de la línea de tiempo, foco D-pad encadenado.
- VOD/Series: la columna de carpetas se oculta al entrar a una carpeta.
- Transiciones: eliminada la animación del riel que parpadeaba.
- Ajustes de TV con iconos (12 categorías con color distintivo).
- Test de velocidad reescrito (multi-endpoint con fallback); DNS privado Cloudflare prendido por defecto.

## v1.9.0
- Reproductor TV estilo TiviMate: info del stream, opciones D-pad, decodificación HW/SW, buffer, AFR, PiP, sleep, favoritos, reproductor externo.
- Detalle: Reanudar / Desde el inicio, contador "1 / N", diálogo Ordenación.
- Ajustes estilo TiviMate: General, Mando a distancia, Control parental con PIN.
- Riel con "Mi lista" e "Historial".
- Fase de guía de TV omitida y EPG eliminado por completo (decisión del dueño).

## v1.8.1
- Reproductor TV propio estilo TiviMate operable con D-pad (progreso, ±10s, Audio, Subtítulos con búsqueda por TMDB ID).
- Reparto clicable con ficha del actor y filmografía; fila "Más como esto".
- 6 pósters por fila en TV.
- Catálogo con caché local e intervalo de actualización configurable (6h/12h/24h/manual + segundo plano).
- Fix del crash `Restore State failed` en la navegación móvil.

## v1.8.0
- Interfaz TV estilo TiviMate: riel de iconos, columna de grupos, grilla de TV en vivo, detalle con backdrop.
- Keystore debug fijo en el repo (las actualizaciones ya no piden desinstalar).

## v1.7.0
- FFmpeg LGPL como respaldo de audio (módulo `decoder_ffmpeg`, solo ARM).
- Media3 actualizado a 1.9.0.

## v1.5.0
- Carpetas como vista principal del catálogo, subtítulos por TMDB ID, fix adelantar/retroceder.

## v1.4.0
- Carpetas del catálogo, orden del proveedor en vivo, Mi biblioteca, reproductor pro, ajustes de subtítulos.

## v1.3.x
- Sin VLC embebido (APK liviano); reproductor externo por Intent.

## v1.2.0
- Rediseño estilo iMPlayer + mejoras de rendimiento.

## v1.1.0
- Descargas Media3 (HLS/DASH), configuración completa, login Xtream persistente cifrado, multi-usuario, tema claro/oscuro, PiP, DNS privado Cloudflare.
- Test de velocidad contra servidor Xtream.

## v1.0.5
- Fix del crash al cargar la lista (ripple incompatible con clickable de Compose).

## v1.0.0
- Versión inicial: login Xtream con 3 servidores precargados, metadata TMDB, reproductor Media3, Compose + TV, Room/DataStore.
