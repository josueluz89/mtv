# Plan: MTV IPTV al estilo TiviMate

Plan analizado de las capturas y el video de referencia de TiviMate enviados por el dueño, con el estado real de implementación.

---

## 1. Lo que mostraba la referencia

### 1.1 TV en vivo (guía)
- Riel de iconos delgado que se expande con etiquetas: Buscar, TV, Películas, Shows, Grabaciones, Mi lista, Opciones (con logo arriba).
- Columna de grupos con estrella de favorito y checks.
- Panel superior con vista previa en vivo del canal enfocado + "Sin información" con franja horaria y progreso.
- Grilla con cabecera de horas, filas numeradas con logo + nombre, celdas "Sin información", indicador de hora actual.

### 1.2 Reproductor (video)
- Barra de info: programa, horario, minutos restantes, canal, badges (FHD, 30 FPS, STEREO).
- Barra de opciones con el control: lista de canales, guía, PiP, retardo de audio (ms), aspecto, sleep timer, añadir a favoritos, opciones.

### 1.3 Detalle de película/serie
- Backdrop, título, rating, año, duración, género, reparto, director, sinopsis.
- Botones: Reanudar, Reproducir desde el inicio, Abrir en reproductor externo, Trailer, Agregar a mi lista.
- Contador "1 / N", diálogo Ordenación (por orden de lista / nombre / calificación / fecha de agregado + agrupar por categorías).

### 1.4 Ajustes
- General, Listas, EPG, Apariencia, Reproducción (buffer, HW/SW audio/video, AFR, passthrough), Mando a distancia, Control parental.

---

## 2. Estado real de implementación

### v1.9.0 — Fases implementadas
- **Fase 1 — Reproductor TV completo**: barra de info del stream (resolución, FPS, audio), barra de opciones con D-pad (lista de canales, PiP, aspecto, sleep, favorito, reproductor externo, opciones), decodificación HW/SW para audio y video, buffer, AFR, audio envolvente por defecto.
- **Fase 2 — OMITIDA por decisión del dueño.** Se había empezado a implementar (vista previa en vivo, estrellas, indicador de hora); se revirtió todo.
- **Fase 3 — Detalle y ordenación**: botones Reanudar / Reproducir desde el inicio, contador "1 / N", diálogo Ordenación (4 criterios + agrupar por categorías).
- **Fase 4 — Ajustes estilo TiviMate**: General (abrir al encender, último canal, PiP al Home, doble Atrás, User-Agent, Respaldar/Restablecer), Mando a distancia (mapeo de teclas con captura), Control parental con PIN.
- **Fase 5 — Riel**: "Mi lista" e "Historial" como secciones del riel, logo de la app arriba.
- **EPG eliminado por completo** por decisión del dueño: sin guía de programación, sin botón "Guía" en el reproductor.

### v1.9.1 — Fixes reportados por el dueño
1. Reproductor reescrito y compacto: línea de tiempo arriba, debajo fila de transporte y fila de opciones; foco D-pad encadenado, nada se solapa ni se corta.
2. VOD/Series: al entrar a una carpeta se oculta la columna de carpetas y el catálogo ocupa todo el ancho; Back vuelve a la vista de columnas.
3. Transiciones: eliminada la animación del ancho del riel que causaba parpadeo.
4. Iconos en Ajustes de TV: 12 categorías con baldosa de color distintivo + icono blanco (solo TV).
5. Test de velocidad reescrito: antes medía contra el servidor Xtream (fallaba); ahora descarga cronometrada contra Cloudflare / CacheFly / OVH con reintentos y fallback, sin requerir sesión. DNS privado Cloudflare (1.1.1.1) prendido por defecto.

### v1.9.2 — Pedidos del dueño
1. **HUD del reproductor estilo Masters TV** (se estudió su repo privado): badges oscuros, tipografía blanca; muestra toda la info del stream — calidad, resolución, FPS, códec de video/audio, bitrate.
2. **Ordenación en series corregida**: causa raíz — el API Xtream manda el timestamp como `last_modified` en series (no `added` como en películas) y el DTO lo descartaba; fix con `@SerialName` + fallback.
3. **Carpetas 20% más pequeñas** (300dp → 240dp) para ver más contenido.
4. **VOD confiable**: causa raíz — `PlayerManager` no observaba fallos y quedaba pantalla negra silenciosa. Nuevo `PlaybackRecovery`: clasificación de fallos, reintentos con backoff, re-login silencioso en 401/403, overlay "Reconectando… (intento N de 3)" y botones Reintentar/Cerrar. Probado 39/39 en JVM.
5. **TV en vivo**: carpetas "Todo" y "★ Favoritos".
6. **Películas y series**: carpeta "Todo" visible con conteo.

---

## 3. Decisiones que se mantienen
- **Sin EPG real ni guía de programación** (decisión del dueño).
- **Sin VLC embebido**: solo reproductor externo por Intent.
- **FFmpeg LGPL** como respaldo de audio (AC3/DTS), solo ARM.
- **Media3 1.9.0**.
- Todo operable con D-pad en TV; 6 pósters por fila.
- Al corregir: reemplazar el código malo, no apilar código encima (regla del dueño).

## 4. Inviable en Media3 1.9.0 (no se implementó)
- Retardo de audio en milisegundos (sin API pública).
- Toggle manual de passthrough (ExoPlayer lo negocia automático).
- Tunneled playback (sin API pública).
