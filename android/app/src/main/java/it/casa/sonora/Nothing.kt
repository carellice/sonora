package it.casa.sonora

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---- Nothing: OLED black, monochrome greys, red only as a signal; Space Grotesk, Space Mono labels, Doto dot-matrix heroes ----
val NdSurface = Color(0xFF111111)
val NdRaised = Color(0xFF1A1A1A)
val NdBorder = Color(0xFF222222)
val NdBorderVisible = Color(0xFF333333)
val NdDisabled = Color(0xFF666666)
val NdSecondary = Color(0xFF999999)
val NdPrimary = Color(0xFFE8E8E8)
val NdRed = Color(0xFFD71921)

// variable fonts: one file per family, the weight picked through the font variation
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val NdSans = FontFamily(grotesk(300), grotesk(400), grotesk(500), grotesk(600), grotesk(700))
val NdMono = FontFamily(Font(R.font.space_mono, FontWeight.Normal), Font(R.font.space_mono_bold, FontWeight.Bold))
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val NdDot = FontFamily(Font(R.font.doto, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))))

/** Base text style of the current skin: Space Grotesk in Nothing, the platform font otherwise. */
val BaseText: TextStyle get() = if (Skin.nothing) TextStyle(fontFamily = NdSans) else TextStyle.Default

/** Instrument-panel label: Space Mono, ALL CAPS, wide tracking. */
@Composable
fun NdLabel(text: String, modifier: Modifier = Modifier, color: Color = NdSecondary, size: TextUnit = 11.sp) =
    Text(text.uppercase(), modifier, color = color, fontSize = size, fontFamily = NdMono, letterSpacing = .08.em, maxLines = 1)

/** Dot-matrix headline (Doto), the one hero moment of a screen. */
@Composable
fun NdHero(text: String, modifier: Modifier = Modifier, size: TextUnit = 44.sp) =
    Text(text, modifier, fontSize = size, fontFamily = NdDot, fontWeight = FontWeight.Bold, letterSpacing = (-.02).em, lineHeight = size, color = Color.White)

/** Faint dot grid behind a surface (the Nothing dot-matrix motif). */
fun Modifier.dotGrid(color: Color = Color(0xFF1C1C1C), step: Dp = 16.dp): Modifier = drawBehind {
    val s = step.toPx()
    val r = 1.dp.toPx()
    var y = s / 2
    while (y < size.height) {
        var x = s / 2
        while (x < size.width) { drawCircle(color, r, Offset(x, y)); x += s }
        y += s
    }
}

/** Full-width 1px divider under a list row. */
fun Modifier.ndDivider(): Modifier = drawBehind { drawLine(NdBorder, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx()) }

/** Outlined pill (secondary buttons, chips, search field). */
fun Modifier.ndOutline(color: Color = NdBorderVisible): Modifier = border(1.dp, color, CircleShape)

/** The signature segmented bar: square blocks with 2dp gaps, filled up to [progress]. */
@Composable
fun SegmentedBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, fill: Color = Color.White, empty: Color = NdBorder) =
    Canvas(modifier.fillMaxWidth().height(height)) {
        val seg = 6.dp.toPx()
        val gap = 2.dp.toPx()
        val n = ((size.width + gap) / (seg + gap)).toInt().coerceAtLeast(1)
        val w = (size.width - gap * (n - 1)) / n
        val lit = (progress.coerceIn(0f, 1f) * n).roundToInt()
        repeat(n) { i -> drawRect(if (i < lit) fill else empty, Offset(i * (w + gap), 0f), Size(w, size.height)) }
    }

/** Seek bar: a segmented bar you tap or drag; reports the fraction while dragging and once more when released. */
@Composable
fun SegmentedSeek(progress: Float, onDrag: (Float) -> Unit, onRelease: (Float) -> Unit, modifier: Modifier = Modifier) {
    val last = remember { floatArrayOf(0f) }
    Box(
        modifier.fillMaxWidth().height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> (o.x / size.width).coerceIn(0f, 1f).let { onDrag(it); onRelease(it) } }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onRelease(last[0]) },
                ) { c, _ -> last[0] = (c.position.x / size.width).coerceIn(0f, 1f); onDrag(last[0]) }
            },
        contentAlignment = Alignment.Center,
    ) { SegmentedBar(progress, height = 14.dp) }
}

/** Hardware-style loader: a row of blocks lit one after the other (no spinners, no skeletons). */
@Composable
fun NdLoader(modifier: Modifier = Modifier, blocks: Int = 10, color: Color = Color.White) {
    val t = rememberInfiniteTransition(label = "ndl")
    val p by t.animateFloat(0f, blocks.toFloat(), infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(blocks) { i ->
            val on = i == p.toInt()
            val near = i == p.toInt() - 1
            Box(Modifier.size(6.dp, 10.dp).background(color.copy(alpha = if (on) 1f else if (near) .45f else .12f)))
        }
    }
}

/** Text navigation at the bottom: [ LIBRERIA ]  CERCA. */
@Composable
fun NothingNavBar(items: List<Pair<String, String>>, isOn: (String) -> Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black)
            .drawBehind { drawLine(NdBorder, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (route, label) ->
            val on = isOn(route)
            Box(
                Modifier.weight(1f).height(60.dp).clickable(remember { MutableInteractionSource() }, null) { onSelect(route) },
                contentAlignment = Alignment.Center,
            ) {
                NdLabel(if (on) "[ $label ]" else label, color = if (on) Color.White else NdDisabled, size = 12.sp)
            }
        }
    }
}

/** Small red dot: "this one is live". */
@Composable
fun RedDot(modifier: Modifier = Modifier) = Box(modifier.size(6.dp).background(NdRed, CircleShape))

/** Zero-padded track number, as on a display: 01, 02… */
fun ndIndex(i: Int): String = i.toString().padStart(2, '0')
