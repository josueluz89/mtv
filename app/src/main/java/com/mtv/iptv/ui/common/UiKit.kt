package com.mtv.iptv.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ---------------- Paleta MTV (oscura, con profundidad) ----------------

val MtvRed = Color(0xFFE02020)
val MtvGold = Color(0xFFF0B429)
val MtvBg = Color(0xFF0D0B0C)
val MtvSurface = Color(0xFF171315)
val MtvSurfaceVariant = Color(0xFF221C1E)
val MtvSurfaceHigh = Color(0xFF2A2326)
val MtvOnBg = Color(0xFFF5F0EB)
val MtvOnVariant = Color(0xFFB8AFA8)

// ---------------- Tema dual móvil + TV ----------------

/**
 * Envuelve el contenido con el tema oscuro MTV para Material3 móvil y para
 * tv-material3 a la vez. Así las pantallas nuevas funcionan en celular (touch)
 * y en Android TV / Firestick (D-pad con estados de foco) sin duplicar código.
 */
@Composable
fun MtvUiTheme(content: @Composable () -> Unit) {
    val mobile = androidx.compose.material3.darkColorScheme(
        primary = MtvRed,
        onPrimary = Color.White,
        secondary = MtvGold,
        onSecondary = Color.Black,
        tertiary = MtvGold,
        background = MtvBg,
        onBackground = MtvOnBg,
        surface = MtvSurface,
        onSurface = MtvOnBg,
        surfaceVariant = MtvSurfaceVariant,
        onSurfaceVariant = MtvOnVariant,
    )
    androidx.compose.material3.MaterialTheme(colorScheme = mobile) {
        androidx.tv.material3.MaterialTheme(
            colorScheme = androidx.tv.material3.darkColorScheme(
                primary = MtvRed,
                onPrimary = Color.White,
                secondary = MtvGold,
                onSecondary = Color.Black,
                background = MtvBg,
                onBackground = MtvOnBg,
                surface = MtvSurface,
                onSurface = MtvOnBg,
                surfaceVariant = MtvSurfaceVariant,
                onSurfaceVariant = MtvOnVariant,
            ),
        ) {
            content()
        }
    }
}

// ---------------- Ordenamiento del catálogo (v1.1, fuera del hilo UI) ----------------

/**
 * Mismas claves y semántica que el ordenamiento v1.1 de CategoryListScreen
 * ("nombre" | "recientes" | "rating" | "anio"). Los llamados DEBEN ejecutarlo
 * con withContext(Dispatchers.Default): con ~86k items, ordenarlo en el hilo
 * UI congela la app.
 */
val catalogSortOptions = listOf(
    "nombre" to "Nombre",
    "recientes" to "Recientes",
    "rating" to "Rating",
    "anio" to "Año",
)

fun catalogSortLabel(key: String): String =
    catalogSortOptions.firstOrNull { it.first == key }?.second ?: "Nombre"

fun yearFromAdded(added: String): Long = try {
    java.time.Instant.ofEpochSecond(added.toLong())
        .atZone(java.time.ZoneId.systemDefault()).year.toLong()
} catch (e: Exception) {
    0L
}

fun <T> sortCatalogItems(
    list: List<T>,
    sortKey: String,
    name: (T) -> String,
    added: (T) -> String,
    rating: (T) -> String,
): List<T> = when (sortKey) {
    "recientes" -> list.sortedByDescending { added(it).toLongOrNull() ?: 0L }
    "rating" -> list.sortedByDescending { rating(it).toDoubleOrNull() ?: 0.0 }
    "anio" -> list.sortedByDescending { yearFromAdded(added(it)) }
    else -> list.sortedBy { name(it).lowercase() }
}

/** "7.5" -> 7.5; nulos, vacíos o <= 0 -> null (sin badge). */
fun parseRating(rating: String): Double? =
    rating.toDoubleOrNull()?.takeIf { it > 0 }

fun buildMeta(year: Int?, rating: Double?, extra: String?): String =
    buildList {
        year?.let { add(it.toString()) }
        rating?.takeIf { it > 0 }?.let { add("★ ${"%.1f".format(it)}") }
        extra?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString("  •  ")

fun openYoutube(context: Context, key: String) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")),
    )
}

// ---------------- Botones (Material3 normal: responden a taps táctiles) ----------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MtvRed,
            contentColor = Color.White,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MtvSurfaceVariant,
            contentColor = MtvOnBg,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

/** Botón "Orden: X" con menú desplegable (mismas opciones que v1.1). */
@Composable
fun SortMenuButton(
    sortKey: String,
    onSortChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SecondaryButton(
            text = "Orden: ${catalogSortLabel(sortKey)}",
            onClick = { open = true },
            icon = Icons.Default.Sort,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            catalogSortOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(if (sortKey == value) "✓ $label" else label) },
                    onClick = { onSortChange(value); open = false },
                )
            }
        }
    }
}

// ---------------- Encabezados ----------------

/** Barra superior: botón Atrás + título + acciones opcionales. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(text = "Atrás", onClick = onBack, icon = Icons.Default.ArrowBack)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MtvOnBg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Encabezado de sección con botón "Ver todo" opcional. */
@Composable
fun SectionHeaderRow(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MtvOnBg,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            SecondaryButton(text = "Ver todo", onClick = onSeeAll)
        }
    }
}

/** Título simple de fila (sin acción). */
@Composable
fun RowTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MtvOnBg,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ---------------- Tarjetas ----------------

/** Badge dorado de rating ("★ 7.5"). */
@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            "★ ${"%.1f".format(rating)}",
            color = MtvGold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Tarjeta de póster 2:3 con degradado, badge de rating y título.
 * Usa Material3 Card normal con onClick: responde a taps táctiles en celular
 * (tv-material3 Card está hecha para foco con D-pad y no responde al tap).
 */
@Composable
fun ImPosterCard(
    imageUrl: String?,
    title: String,
    rating: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 128.dp,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(cardWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MtvSurface,
            contentColor = MtvOnBg,
        ),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MtvSurfaceVariant),
            )
            // Degradado inferior para dar profundidad y legibilidad al título.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                        RoundedCornerShape(12.dp),
                    ),
            )
            if (rating != null && rating > 0) {
                RatingBadge(
                    rating = rating,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }
    }
}

// ---------------- Hero ----------------

/**
 * Hero del #1 del catálogo: backdrop grande con degradados, insignia,
 * título, metadata, sinopsis y acciones (Ver ahora / Trailer).
 */
@Composable
fun CatalogHero(
    backdropUrl: String?,
    badge: String,
    title: String,
    meta: String,
    overview: String?,
    onPlay: () -> Unit,
    onTrailer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MtvSurface),
    ) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(MtvSurfaceVariant),
        )
        // Profundidad: degradado vertical + lateral oscuro.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                    ),
                    RoundedCornerShape(20.dp),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    RoundedCornerShape(20.dp),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MtvRed)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(badge, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MtvGold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (!overview.isNullOrBlank()) {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                PrimaryButton(
                    text = "Ver ahora",
                    onClick = onPlay,
                    icon = Icons.Default.PlayArrow,
                )
                if (onTrailer != null) {
                    SecondaryButton(
                        text = "Trailer",
                        onClick = onTrailer,
                        icon = Icons.Default.SmartDisplay,
                    )
                }
            }
        }
    }
}
