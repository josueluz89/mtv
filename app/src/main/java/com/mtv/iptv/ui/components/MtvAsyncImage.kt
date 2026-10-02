package com.mtv.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.mtv.iptv.di.LocalAppContainer

/**
 * Wrapper de Coil para toda la app (pósters, logos, fotos de reparto...).
 *
 * Diferencias con usar `coil.compose.AsyncImage` directo:
 * - Usa el [ImageLoader][coil.ImageLoader] singleton del [AppContainer]
 *   (caché de memoria acotado a ~10% del heap, tope 64 MB).
 * - Limita el bitmap decodificado con `size([maxSizePx])`: los pósters de
 *   los paneles Xtream suelen ser imágenes grandes y decodificarlas a
 *   resolución completa revienta la RAM en dispositivos chicos. El default
 *   (512 px de lado máximo) alcanza para tarjetas y grillas; las pantallas
 *   de detalle pueden subirlo a 1024.
 *
 * La UI debería migrar sus AsyncImage a este wrapper (ver ui/mobile/components.kt).
 */
@Composable
fun MtvAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** Lado máximo (px) del bitmap decodificado. */
    maxSizePx: Int = 512,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val request = remember(model, maxSizePx) {
        ImageRequest.Builder(context)
            .data(model)
            // Acota el bitmap decodificado: evita OOM con pósters gigantes.
            .size(maxSizePx)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        imageLoader = container.imageLoader,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onSuccess = { onSuccess?.invoke(it) },
        onError = { onError?.invoke(it) },
    )
}

/**
 * Variante sin descripción (decorativa) para no repetir el contentDescription = null.
 */
@Composable
fun MtvAsyncImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxSizePx: Int = 512,
) {
    MtvAsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        maxSizePx = maxSizePx,
    )
}
