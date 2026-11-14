package it.casa.sonora

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Modern UI (Windows Phone): black, flat, square, one accent colour, big light lowercase type ----
val MetroAccent = Color(0xFF1BA1E2) // "cyan", the classic Windows Phone accent
val MetroChrome = Color(0xFF1F1F1F) // app bar and message boxes
val MetroSubtle = Color(0xFF999999)

/** Rounded corners in the other styles, square in Modern UI. */
fun corner(r: Dp): Shape = if (Skin.metro) RectangleShape else RoundedCornerShape(r)

/** Modern UI writes titles and labels in lowercase. */
fun String.mt(): String = if (Skin.metro) lowercase() else this

/** Round app-bar button: 2dp white ring. */
fun Modifier.ring(color: Color = Color.White): Modifier = border(2.dp, color, CircleShape).clip(CircleShape)

/** Windows Phone page entrance: the page swings in like a turnstile, hinged on the left edge. */
@Composable
fun Turnstile(content: @Composable () -> Unit) {
    if (!Skin.metro) return content()
    val a = remember { Animatable(-75f) }
    LaunchedEffect(Unit) { a.animateTo(0f, tween(380, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    Box(
        Modifier.graphicsLayer {
            rotationY = a.value; transformOrigin = TransformOrigin(0f, .5f); cameraDistance = 10 * density
            alpha = 1f + a.value / 150f
        },
    ) { content() }
}

/** The "flying dots" progress indicator: five dots that race in, slow down in the middle and race out. */
@Composable
fun MetroDots(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(0f, 1.4f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p")
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val d = 4.dp.toPx()
        repeat(5) { i ->
            val k = p - i * .08f
            if (k <= 0f || k >= 1f) return@repeat
            val e = k - .5f
            drawRect(MetroAccent, Offset((.5f + 4 * e * e * e) * (size.width - d), 0f), Size(d, d))
        }
    }
}

/** Pivot headers: big lowercase words, the current one white, the others dim; tapping or swiping the page switches. */
@Composable
fun PivotHeaders(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem(selected) }
    // padded outside the list, so the item before the current one is clipped away instead of peeking in from the left
    LazyRow(modifier.fillMaxWidth().padding(start = 12.dp), state = state, contentPadding = PaddingValues(end = 400.dp)) {
        itemsIndexed(tabs) { i, t ->
            val c by animateColorAsState(if (i == selected) Color.White else Color(0xFF555555), label = "pivot")
            Text(
                t.lowercase(), color = c, fontSize = 38.sp, fontWeight = FontWeight.Light, maxLines = 1,
                modifier = Modifier.clickable { onSelect(i) }.padding(horizontal = 8.dp),
            )
        }
    }
}

/** Horizontal swipe over a pivot page: left = next item, right = previous one (wraps around, like on the phone). */
fun Modifier.pivotSwipe(count: Int, selected: Int, onSelect: (Int) -> Unit): Modifier =
    if (!Skin.metro) this else pointerInput(selected, count) {
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragEnd = {
                if (dx < -140f) onSelect((selected + 1) % count) else if (dx > 140f) onSelect((selected - 1 + count) % count)
            },
        ) { _, d -> dx += d }
    }

/** Small uppercase app title above pivots ("SONORA"). */
@Composable
fun MetroTitle(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = modifier)
