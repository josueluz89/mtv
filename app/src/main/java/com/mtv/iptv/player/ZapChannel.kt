package com.mtv.iptv.player

/**
 * Canal disponible para zapping dentro del reproductor interno.
 * Se pasa desde la lista de TV en vivo para permitir cambiar de canal
 * (anterior/siguiente o lista rápida) sin salir del reproductor.
 */
data class ZapChannel(
    val streamId: Int,
    val name: String,
    val num: Int,
    val icon: String = "",
)
