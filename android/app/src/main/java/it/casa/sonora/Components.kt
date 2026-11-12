package it.casa.sonora

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable

val Bg = Color(0xFF0B0B10)
val Card = Color(0xFF17161F)
val CardHi = Color(0xFF221F2E)
val Accent = Color(0xFFFF4D6D)
val Accent2 = Color(0xFF8B5CF6)
val Dim = Color(0xFF9C99B0)
val AccentBrush = Brush.linearGradient(listOf(Accent, Accent2))

@Composable
fun SonoraTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Accent, secondary = Accent2, background = Bg, surface = Bg,
        surfaceVariant = Card, surfaceContainer = Card, surfaceContainerHigh = CardHi, surfaceContainerLow = Card,
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Dim, onPrimary = Color.White,
        secondaryContainer = CardHi, onSecondaryContainer = Color.White,
    ),
    content = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White, content = content) },
)

fun fmt(sec: Int): String = if (sec <= 0) "" else "%d:%02d".format(sec / 60, sec % 60)
fun fmtMs(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp), icon: ImageVector = Icons.Rounded.MusicNote) {
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(CardHi, Card))), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Dim.copy(alpha = .6f), modifier = Modifier.fillMaxSize(.38f))
        if (url != null) AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(350).build(),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Dark, saturated color picked from the artwork; animates when the artwork changes. */
@Composable
fun rememberArtColor(url: String?, fallback: Color = CardHi): Color {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        if (url == null) { target = fallback; return@LaunchedEffect }
        val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(128).build())
        val bmp = ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val p = Palette.from(bmp).generate()
        val rgb = p.vibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: return@LaunchedEffect
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(rgb, it) }
        hsl[2] = hsl[2].coerceIn(.22f, .34f)
        target = Color(ColorUtils.HSLToColor(hsl))
    }
    return animateColorAsState(target, tween(700), label = "art").value
}

/** Shrinks slightly while pressed: makes every tap feel physical. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .96f else 1f, spring(stiffness = 600f), label = "press")
    return scale(s)
}

@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Accent) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(420, 300, 520).forEach { d ->
            val h by t.animateFloat(.25f, 1f, infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar")
            Box(
                Modifier.width(4.dp).fillMaxHeight(if (playing) h else .3f)
                    .clip(RoundedCornerShape(2.dp)).background(color),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    t: Track, onClick: () -> Unit, onMore: () -> Unit,
    index: Int? = null, subtitle: String = listOfNotNull(t.artist.ifEmpty { null }, t.album).joinToString(" • "),
) {
    val isCurrent = Playback.current?.id == t.id
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (isCurrent) Accent.copy(alpha = .10f) else Color.Transparent, label = "rowbg")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .combinedClickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onMore)
            .pressScale(src).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) EqualizerBars(Playback.isPlaying)
                else Text("$index", color = Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        } else Box(contentAlignment = Alignment.Center) {
            Artwork(t.thumb, Modifier.size(50.dp))
            if (isCurrent) Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                EqualizerBars(Playback.isPlaying, color = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (isCurrent) Accent else Color.White,
            )
            Text(
                listOf(subtitle, fmt(t.duration)).filter { it.isNotEmpty() }.joinToString(" • "),
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 13.sp,
            )
        }
        DownloadMark(t.id)
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "Opzioni", tint = Dim) }
    }
}

/** Small download state next to a song: progress while downloading, a check when saved on the phone. */
@Composable
fun DownloadMark(id: String) {
    val progress = Downloads.active[id]
    when {
        progress != null -> androidx.compose.material3.CircularProgressIndicator(
            progress = { progress }, modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp, trackColor = Dim.copy(alpha = .25f),
        )
        Downloads.isSaved(id) -> Icon(Icons.Rounded.DownloadDone, "Scaricato", tint = Accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun AlbumCard(title: String, subtitle: String, thumb: String?, modifier: Modifier = Modifier, size: Dp? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.then(if (size != null) Modifier.width(size) else Modifier)
            .pressScale(src).clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick).padding(6.dp),
    ) {
        Artwork(thumb, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(12.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp)
    }
}

@Composable
fun ArtistRow(a: Artist, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .pressScale(src).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.thumb != null) Artwork(a.thumb, Modifier.size(56.dp), CircleShape)
        else Box(Modifier.size(56.dp).clip(CircleShape).background(AccentBrush), contentAlignment = Alignment.Center) {
            Text(a.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Artista", color = Dim, fontSize = 13.sp)
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, big: Boolean = false) {
    val focusBg by animateColorAsState(if (value.isEmpty()) Card else CardHi, label = "sf")
    Row(
        modifier.fillMaxWidth().height(if (big) 52.dp else 44.dp).clip(RoundedCornerShape(if (big) 26.dp else 22.dp)).background(focusBg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Dim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Dim, fontSize = if (big) 16.sp else 14.sp, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true, cursorBrush = SolidColor(Accent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontSize = if (big) 16.sp else 14.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(
            Icons.Rounded.Close, "Cancella", tint = Dim,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
        )
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.pressScale(src).height(48.dp).clip(RoundedCornerShape(24.dp)).background(AccentBrush)
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun SoftButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.pressScale(src).height(48.dp).clip(RoundedCornerShape(24.dp)).background(CardHi)
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Card), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Dim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Marquee(text: String, modifier: Modifier = Modifier, size: Int = 15, weight: FontWeight = FontWeight.Normal, color: Color = Color.White) =
    Text(text, modifier.basicMarquee(iterations = Int.MAX_VALUE), maxLines = 1, fontSize = size.sp, fontWeight = weight, color = color)

