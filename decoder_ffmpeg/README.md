# FFmpeg (LGPL 2.1+) — decodificador de audio de respaldo para MTV IPTV

Este módulo es `libraries/decoder_ffmpeg` de
[androidx/media](https://github.com/androidx/media) tag `1.9.0`, copiado
verbatim (licencia Apache 2.0; los headers de licencia de cada archivo están
intactos). Se excluyó a propósito el renderer de video experimental: el
respaldo es **solo audio**.

## Licencia del binario

El FFmpeg nativo se compila con `--disable-gpl --disable-nonfree`
(**LGPL 2.1+**, sin componentes GPL) y solo con decodificadores de audio
propios de FFmpeg:

`vorbis opus flac alac pcm_mulaw pcm_alaw mp3 aac ac3 eac3 dca mlp truehd`

El enlazado es estático dentro de `libffmpegJNI.so` (procedimiento estándar
de upstream). Conformidad LGPL 2.1 §6:

1. Este repo incluye los scripts exactos de compilación
   (`src/main/jni/build_ffmpeg.sh`).
2. El CI (`.github/workflows/build-apk.yml`) compila FFmpeg desde el código
   fuente oficial (`git.ffmpeg.org`, rama `release/6.0`, la recomendada por
   media3 1.9.0).
3. El CI publica las bibliotecas compiladas como artifact (`ffmpeg-lgpl-libs`).
4. Atribución y oferta de fuentes en la app: **Ajustes → Acerca de**.
5. Para recompilar: ver `.github/workflows/build-apk.yml` (paso FFmpeg).

## Recompilar localmente (referencia)

```bash
git clone --depth 1 -b 1.9.0 https://github.com/androidx/media.git  # solo el módulo
git clone --depth 1 -b release/6.0 https://git.ffmpeg.org/ffmpeg.git decoder_ffmpeg/src/main/jni/ffmpeg
cd decoder_ffmpeg/src/main/jni
./build_ffmpeg.sh "$PWD/.." "$ANDROID_NDK" linux-x86_64 26 \
  vorbis opus flac alac pcm_mulaw pcm_alaw mp3 aac ac3 eac3 dca mlp truehd
```

ABIs compiladas: `armeabi-v7a` y `arm64-v8a` (teléfonos, Android TV, Firestick).
