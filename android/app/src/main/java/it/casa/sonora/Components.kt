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
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** List row container: rounded highlight by default, full-width engraved groove in skeuomorphism. */
fun Modifier.listRow(): Modifier =
    if (Skin.skeu) padding(horizontal = 12.dp).drawBehind {
        val y = size.height - 1f
        drawLine(Color.Black.copy(alpha = .55f), Offset(0f, y - 1f), Offset(size.width, y - 1f), 2f)
        drawLine(Color.White.copy(alpha = .05f), Offset(0f, y + 1f), Offset(size.width, y + 1f), 1f)
    }
    else if (Skin.metro) padding(horizontal = 12.dp)
    else if (Skin.nothing) padding(horizontal = 16.dp).ndDivider()
    else padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp))

/** Round icon-button background: a domed knob in skeuomorphism, a faint disc otherwise. */
fun Modifier.roundButton(): Modifier = if (Skin.skeu) knob() else if (Skin.metro) ring()
    else if (Skin.nothing) clip(CircleShape).background(NdSurface).border(1.dp, NdBorderVisible, CircleShape)
    else clip(CircleShape).background(Color.White.copy(alpha = .08f))

/** Light text with a letterpress shadow on leather (skeuomorphism only). */
fun skeuText(): TextStyle = if (Skin.skeu) Engraved else BaseText

// getters: they follow the style chosen in Impostazioni (Skin.style is Compose state, so screens recompose)
val Bg: Color get() = if (Skin.skeu) Color(0xFF1D1A17) else if (Skin.metro || Skin.nothing) Color.Black else Color(0xFF0B0B10)
val Card: Color get() = if (Skin.skeu) Color(0xFF2C2824) else if (Skin.metro) MetroChrome else if (Skin.nothing) NdSurface else Color(0xFF17161F)
val CardHi: Color get() = if (Skin.skeu) Color(0xFF36312C) else if (Skin.metro) MetroChrome else if (Skin.nothing) NdSurface else Color(0xFF221F2E)
// Nothing: white is the "accent"; red is used on purpose only (playing now, saved, toggles on)
val Accent: Color get() = if (Skin.skeu) Amber else if (Skin.metro) MetroAccent else if (Skin.nothing) Color.White else Color(0xFFFF4D6D)
val Accent2: Color get() = if (Skin.skeu) Color(0xFFFF7A1A) else if (Skin.metro) MetroAccent else if (Skin.nothing) NdSecondary else Color(0xFF8B5CF6)
val Dim: Color get() = if (Skin.skeu) Color(0xFFA89F94) else if (Skin.metro) MetroSubtle else if (Skin.nothing) NdSecondary else Color(0xFF9C99B0)
val AccentBrush: Brush get() = if (Skin.skeu) AmberGloss else if (Skin.metro) SolidColor(MetroAccent) else if (Skin.nothing) SolidColor(NdRaised)
    else Brush.linearGradient(listOf(Accent, Accent2))
private val Paper = Color(0xFFF4ECE2)
private val Cocoa = Color(0xFF4A2A00)

// Modern UI: square dialogs, sheets and fields; light headlines
private val SquareShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp),
)
private val MetroType = androidx.compose.material3.Typography().let {
    it.copy(headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp))
}
// Nothing: dialogs and sheets up to 16dp, everything in Space Grotesk
private val NdShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(16.dp),
)
private val NdType = androidx.compose.material3.Typography().run {
    fun TextStyle.nd() = copy(fontFamily = NdSans)
    copy(
        displayLarge.nd(), displayMedium.nd(), displaySmall.nd(), headlineLarge.nd(), headlineMedium.nd(),
        headlineSmall.nd().copy(fontWeight = FontWeight.Normal, fontSize = 22.sp), titleLarge.nd(), titleMedium.nd(), titleSmall.nd(),
        bodyLarge.nd(), bodyMedium.nd(), bodySmall.nd(), labelLarge.nd(), labelMedium.nd(), labelSmall.nd(),
    )
}

@Composable
fun SonoraTheme(content: @Composable () -> Unit) = MaterialTheme(
    shapes = if (Skin.metro) SquareShapes else if (Skin.nothing) NdShapes else androidx.compose.material3.Shapes(),
    typography = if (Skin.metro) MetroType else if (Skin.nothing) NdType else androidx.compose.material3.Typography(),
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
    val shape = if (Skin.metro) RectangleShape else shape
    Box(
        modifier.clip(shape).background(Brush.linearGradient(listOf(CardHi, Card))).then(if (Skin.skeu) Modifier.glossOverlay() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
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
                    .clip(corner(2.dp)).background(color),
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
    val bg by animateColorAsState(if (isCurrent && !Skin.metro && !Skin.nothing) Accent.copy(alpha = if (Skin.skeu) .14f else .10f) else Color.Transparent, label = "rowbg")
    Row(
        Modifier.fillMaxWidth().listRow().background(bg)
            .combinedClickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onMore)
            .pressScale(src).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) EqualizerBars(Playback.isPlaying)
                else if (Skin.nothing) Text(ndIndex(index), color = NdDisabled, fontSize = 13.sp, fontFamily = NdMono)
                else Text(
                    "$index", color = Dim, fontSize = if (Skin.metro) 20.sp else 15.sp,
                    fontWeight = if (Skin.skeu) FontWeight.Bold else if (Skin.metro) FontWeight.Light else FontWeight.Medium,
                    fontFamily = if (Skin.skeu) FontFamily.Monospace else null,
                )
            }
        } else Box(contentAlignment = Alignment.Center) {
            Artwork(t.thumb, Modifier.size(50.dp))
            if (isCurrent) Box(Modifier.size(50.dp).clip(corner(10.dp)).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                EqualizerBars(Playback.isPlaying, color = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent && Skin.nothing) { RedDot(); Spacer(Modifier.width(8.dp)) }
                Text(
                    t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (Skin.metro || Skin.nothing) FontWeight.Normal else FontWeight.SemiBold, fontSize = if (Skin.metro) 18.sp else 15.sp,
                    color = if (isCurrent) Accent else if (Skin.skeu) Paper else Color.White,
                    style = if (!Skin.skeu) BaseText else if (isCurrent) glow() else Engraved,
                )
            }
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
            .pressScale(src).clip(corner(14.dp))
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick).padding(6.dp),
    ) {
        if (Skin.skeu) Artwork(thumb, Modifier.fillMaxWidth().aspectRatio(1f).shadow(8.dp, RoundedCornerShape(4.dp)), RoundedCornerShape(4.dp))
        else Artwork(thumb, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(12.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (Skin.metro || Skin.nothing) FontWeight.Normal else FontWeight.SemiBold,
            fontSize = if (Skin.metro) 16.sp else 14.sp, style = skeuText(),
        )
        if (Skin.nothing) NdLabel(subtitle, Modifier.padding(top = 4.dp), size = 10.sp)
        else Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp)
    }
}

@Composable
fun ArtistRow(a: Artist, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().listRow()
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .pressScale(src).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.thumb != null) Artwork(a.thumb, Modifier.size(56.dp), CircleShape)
        else Box(
            Modifier.size(56.dp).clip(if (Skin.metro) RectangleShape else CircleShape).background(AccentBrush)
                .then(if (Skin.nothing) Modifier.border(1.dp, NdBorderVisible, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                a.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (Skin.nothing) 28.sp else 22.sp,
                color = if (Skin.skeu) Cocoa else Color.White, style = if (Skin.skeu) OnMetal else BaseText,
                fontFamily = if (Skin.nothing) NdDot else null,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.name, fontWeight = if (Skin.metro) FontWeight.Light else if (Skin.nothing) FontWeight.Normal else FontWeight.SemiBold, fontSize = if (Skin.metro) 22.sp else 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = skeuText(),
            )
            if (Skin.nothing) NdLabel("Artista", Modifier.padding(top = 2.dp), size = 10.sp) else Text("Artista".mt(), color = Dim, fontSize = 13.sp)
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, big: Boolean = false) {
    val metro = Skin.metro
    val focusBg by animateColorAsState(
        if (metro) (if (value.isEmpty()) Color(0xFFCCCCCC) else Color.White) else if (value.isEmpty()) Card else CardHi, label = "sf",
    )
    val ink = if (metro) Color(0xFF555555) else Dim
    Row(
        modifier.fillMaxWidth().height(if (big) 52.dp else 44.dp).then(
            if (Skin.skeu) Modifier.inset(RoundedCornerShape(if (big) 26.dp else 22.dp))
            else if (Skin.nothing) Modifier.ndOutline(if (value.isEmpty()) NdBorderVisible else NdPrimary)
            else Modifier.clip(corner(if (big) 26.dp else 22.dp)).background(focusBg),
        ).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder.mt(), color = if (Skin.nothing) NdDisabled else ink, fontSize = if (big) 16.sp else 14.sp, maxLines = 1, fontFamily = if (Skin.nothing) NdMono else null)
            BasicTextField(
                value, onChange, singleLine = true, cursorBrush = SolidColor(Accent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (metro) Color.Black else Color.White, fontSize = if (big) 16.sp else 14.sp, fontFamily = if (Skin.nothing) NdMono else null,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(
            Icons.Rounded.Close, "Cancella", tint = ink,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
        )
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.pressScale(src).height(48.dp).then(
            if (Skin.skeu) Modifier.glossAmber(RoundedCornerShape(12.dp)) else if (Skin.nothing) Modifier.clip(CircleShape).background(Color.White)
            else Modifier.clip(corner(24.dp)).background(AccentBrush),
        )
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (Skin.skeu) Cocoa else if (Skin.nothing) Color.Black else Color.White
        Icon(icon, null, tint = fg)
        Spacer(Modifier.width(8.dp))
        if (Skin.nothing) NdLabel(text, color = fg, size = 13.sp)
        else Text(text.mt(), fontWeight = if (Skin.metro) FontWeight.Normal else FontWeight.Bold, fontSize = if (Skin.metro) 17.sp else 14.sp, color = fg, style = if (Skin.skeu) OnMetal else TextStyle.Default)
    }
}

@Composable
fun SoftButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.pressScale(src).height(48.dp).then(
            if (Skin.skeu) Modifier.metal(RoundedCornerShape(12.dp)) else if (Skin.metro) Modifier.border(2.dp, Color.White)
            else if (Skin.nothing) Modifier.ndOutline().clip(CircleShape)
            else Modifier.clip(RoundedCornerShape(24.dp)).background(CardHi),
        )
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (Skin.skeu) MetalText else if (Skin.nothing) NdPrimary else Color.White
        Icon(icon, null, tint = fg)
        Spacer(Modifier.width(8.dp))
        if (Skin.nothing) NdLabel(text, color = fg, size = 13.sp)
        else Text(text.mt(), fontWeight = if (Skin.metro) FontWeight.Normal else FontWeight.SemiBold, fontSize = if (Skin.metro) 17.sp else 14.sp, color = fg, style = if (Skin.skeu) OnMetal else TextStyle.Default)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    if (Skin.nothing) return NdEmptyState(icon, title, text)
    Column(Modifier.fillMaxWidth().padding(top = 70.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).then(if (Skin.skeu) Modifier.knob() else if (Skin.metro) Modifier.background(MetroAccent) else Modifier.clip(CircleShape).background(Card)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (Skin.metro) Color.White else Accent, modifier = Modifier.size(if (Skin.metro) 44.dp else 38.dp))
        }
        Spacer(Modifier.height(18.dp))
        if (Skin.metro) Text(title.lowercase(), fontWeight = FontWeight.Light, fontSize = 28.sp)
        else Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, style = skeuText())
        Spacer(Modifier.height(6.dp))
        Text(text, color = Dim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Nothing empty state: a dot-matrix disc, a grey headline, one quiet sentence. */
@Composable
private fun NdEmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).dotGrid(NdBorderVisible, 8.dp).border(1.dp, NdBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = NdDisabled, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 18.sp, color = NdSecondary)
        Spacer(Modifier.height(6.dp))
        Text(text, color = NdDisabled, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Marquee(
    text: String, modifier: Modifier = Modifier, size: Int = 15, weight: FontWeight = FontWeight.Normal, color: Color = Color.White,
    style: TextStyle = BaseText, mono: Boolean = false,
) = Text(
    text, modifier.basicMarquee(iterations = Int.MAX_VALUE), maxLines = 1, fontSize = size.sp, fontWeight = weight, color = color,
    style = style, fontFamily = if (mono) (if (Skin.nothing) NdMono else FontFamily.Monospace) else null,
)

