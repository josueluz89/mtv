/*
 * Módulo FFmpeg para MTV IPTV (vendored de androidx/media tag 1.9.0,
 * libraries/decoder_ffmpeg — Apache 2.0; headers de licencia intactos).
 *
 * Provee FfmpegAudioRenderer como decodificador de audio por software
 * (respaldo cuando el dispositivo no soporta el codec). El FFmpeg nativo
 * se compila LGPL 2.1+ (--disable-gpl --disable-nonfree) vía
 * src/main/jni/build_ffmpeg.sh en CI. Ver README.md del módulo.
 */

plugins {
    id("com.android.library")
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // Solo ARM: coincide con las ABIs que compila build_ffmpeg.sh
        // (teléfonos, Android TV, Firestick).
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
}

// El build nativo solo se configura si FFmpeg ya fue compilado, para que
// el proyecto sincronice sin NDK (mismo patrón que upstream).
if (project.file("src/main/jni/ffmpeg").exists()) {
    android.externalNativeBuild.cmake.path = file("src/main/jni/CMakeLists.txt")
    android.externalNativeBuild.cmake.version = "3.22.1+"
}

dependencies {
    api("androidx.media3:media3-decoder:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.annotation:annotation:1.9.1")
    compileOnly("org.checkerframework:checker-qual:3.13.0")
}
