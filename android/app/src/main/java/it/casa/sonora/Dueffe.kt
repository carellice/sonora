package it.casa.sonora

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---- Dueffe: near-black ground, warm surfaces, ivory text, one gold accent; Bricolage titles, Inter text ----
val DfBg = Color(0xFF050505)
val DfSurface = Color(0xFF11100D)
val DfSurfaceHigh = Color(0xFF1A1711)
val DfSurfaceHigher = Color(0xFF242016)
val DfText = Color(0xFFFFFAF0)
val DfMuted = Color(0xFFB8AA8D)
val DfLine = Color(0x29FFDC80)
val DfLineStrong = Color(0x57FFD84D)
val DfPrimary = Color(0xFFFFD84D)
val DfPrimaryStrong = Color(0xFFFFB800)
val DfSecondary = Color(0xFFFFF0AD)
val DfOnPrimary = Color(0xFF161006)
val DfError = Color(0xFFFF9B85)
val DfNav = Color(0xF0080705)

val DfReveal = CubicBezierEasing(.22f, .78f, .16f, 1f)
val DfLetter = CubicBezierEasing(.16f, .86f, .24f, 1f)

/** Gold fill of the primary actions: accent into a mix with Primary Strong, at 135°. */
val DfGold = Brush.linearGradient(listOf(DfPrimary, Color(0xFFFFC826)))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun bricolage(w: Int) = Font(R.font.bricolage, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val DfDisplay = FontFamily(bricolage(400), bricolage(500), bricolage(600), bricolage(700), bricolage(800))
val DfBody = FontFamily(inter(400), inter(500), inter(600), inter(700))

val DfType = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = DfDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-0.035).em),
    displayMedium = TextStyle(fontFamily = DfDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-0.03).em),
    displaySmall = TextStyle(fontFamily = DfDisplay, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = DfDisplay, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = DfDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = DfDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = DfDisplay, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = DfBody, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = DfBody, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = DfBody, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = DfBody, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = DfBody, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = DfBody, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = DfBody, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em),
)

/** Titles: Bricolage, tight tracking. */
val DfTitle = TextStyle(fontFamily = DfDisplay, letterSpacing = (-0.02).em)

/** Card: vertical gradient Surface High into Surface, 1px gold line, no shadow. */
fun Modifier.dfCard(shape: Shape = RoundedCornerShape(26.dp)): Modifier =
    clip(shape).background(Brush.verticalGradient(listOf(DfSurfaceHigh, DfSurface))).border(1.dp, DfLine, shape)

/** Page title in Display S, the last word gold with a hand-drawn underline that draws itself in. */
@Composable
fun DfHeadline(text: String, modifier: Modifier = Modifier) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(700, 220, DfReveal)) }
    Text(
        text, modifier.padding(bottom = 6.dp).drawBehind {
            val y = size.height + 1.dp.toPx()
            val w = size.width * draw.value
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y)
                quadraticTo(w * .45f, y - 5.dp.toPx() * draw.value, w, y - 1.dp.toPx())
            }
            drawPath(path, DfPrimaryStrong, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        },
        color = DfPrimary, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, style = DfTitle,
    )
}

/** Detail backdrop: the artwork blurred (60, 55%), a glow of its colour, fading into the ground. */
@Composable
fun DfHeroBackdrop(url: String?, tint: Color) = Box(Modifier.fillMaxSize()) {
    DfAurora()
    coil.compose.AsyncImage(
        url, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(.62f).blur(60.dp).alpha(.45f),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint.copy(alpha = .2f), .3f to DfBg.copy(alpha = .35f), .56f to DfBg)))
}

/** Fades the content out towards the bottom, so a hero image melts into the aurora instead of ending on a seam. */
fun Modifier.dfFadeOut(): Modifier = graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(.45f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

/** Aurora: three gold and amber nebulae drifting in 32s, and ivory dust rising and twinkling. */
@Composable
fun DfAurora(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(32_000, easing = LinearEasing)), label = "a")
    val dust = remember { List(34) { i -> floatArrayOf(seed(i * 3 + 1), seed(i * 7 + 2), .35f + seed(i * 11 + 3), .6f + seed(i * 5 + 4) * 1.8f) } }
    Canvas(modifier.fillMaxSize()) {
        drawRect(DfBg)
        val w = size.width
        val h = size.height
        blob(Offset(w * (.18f + .14f * cos(a)), h * .06f + 50f * sin(a * 2)), w * .95f, Color(0x38FFB800))
        blob(Offset(w * (.95f + .08f * sin(a)), h * (.38f + .05f * cos(a))), w * .75f, Color(0x24FFD84D))
        blob(Offset(w * (.35f + .2f * sin(a + 1.3f)), h * (.92f + .04f * sin(a * 3))), w * 1.05f, Color(0x1F8A5A00))
        val phase = a / (2 * PI).toFloat()
        dust.forEach { (x, y0, speed, r) ->
            val y = ((y0 - phase * speed) % 1f + 1f) % 1f
            val twinkle = .5f + .5f * sin(a * 6 * speed + x * 20)
            drawCircle(DfSecondary.copy(alpha = .08f + .28f * twinkle * (1f - y * .6f)), r * density, Offset(w * x + 14f * sin(a * 2 + y0 * 9), h * y))
        }
    }
}

private fun seed(i: Int): Float = (sin(i * 12.9898f) * 43758.547f).let { it - kotlin.math.floor(it) }

private fun DrawScope.blob(center: Offset, radius: Float, color: Color) =
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)

/**
 * Floating bottom bar: 68 high, radius 28, gold indicator under the chosen tab.
 * The indicator is "liquid": the edge it moves towards arrives first (260ms), the other follows (620ms).
 */
@Composable
fun DueffeNavBar(items: List<Triple<String, String, ImageVector>>, isOn: (String) -> Boolean, onSelect: (String) -> Unit) {
    val sel = items.indexOfFirst { isOn(it.first) }.coerceAtLeast(0)
    BoxWithConstraints(
        Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().height(68.dp)
            .clip(RoundedCornerShape(28.dp)).background(DfNav).border(1.dp, DfLine, RoundedCornerShape(28.dp)).padding(6.dp),
    ) {
        val w = maxWidth / items.size
        val goingRight = remember { booleanArrayOf(true) }
        val left by animateDpAsState(w * sel, tween(if (goingRight[0]) 620 else 260, easing = DfReveal), label = "l")
        val right by animateDpAsState(w * (sel + 1), tween(if (goingRight[0]) 260 else 620, easing = DfReveal), label = "r")
        Box(Modifier.offset(x = left).width(right - left).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(DfGold))
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, (route, label, icon) ->
                val on = i == sel
                val fg by animateColorAsState(if (on) DfOnPrimary else DfMuted, tween(260), label = "fg")
                val bounce = remember { Animatable(1f) }
                LaunchedEffect(on) { if (on) { bounce.snapTo(.8f); bounce.animateTo(1f, spring(dampingRatio = .42f, stiffness = 380f)) } }
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp))
                        .clickable(remember { MutableInteractionSource() }, null) { goingRight[0] = i > sel; onSelect(route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp).scale(bounce.value))
                    Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = DfBody, maxLines = 1)
                }
            }
        }
    }
}
