package it.casa.sonora

import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Graphic style picked in Impostazioni → Stile grafica: "classic", "skeu" (skeuomorphism, "Hi-Fi") or "metro" (Modern UI). */
object Skin {
    val styles = listOf("classic", "skeu", "metro")
    var style by mutableStateOf("classic")
        private set
    val skeu get() = style == "skeu"
    val metro get() = style == "metro"
    private var prefs: SharedPreferences? = null

    fun init(p: SharedPreferences) { prefs = p; style = p.getString("skin", "")?.takeIf { it in styles } ?: "classic" }
    fun set(v: String) { style = v; prefs?.edit()?.putString("skin", v)?.apply() }
}

// ---- palette: dark leather, brushed aluminium, amber LED ----
val Amber = Color(0xFFFFB23E)
val MetalText = Color(0xFF2A2C30)
val MetalDim = Color(0xFF55585D)
val LcdText = Color(0xFFFFB23E)
private val LeatherHi = Color(0xFF3A342E)
private val LeatherLo = Color(0xFF1D1A17)

/** Grain textures, generated once: no image assets needed. */
private fun bitmap(w: Int, h: Int, px: (Int, Int) -> Int): ImageBitmap {
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val arr = IntArray(w * h) { px(it % w, it / w) }
    bmp.setPixels(arr, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}

private val noise by lazy {
    val r = Random(7)
    ShaderBrush(ImageShader(bitmap(128, 128) { _, _ -> val a = r.nextInt(0, 34); (a shl 24) or (if (r.nextBoolean()) 0xFFFFFF else 0) }, TileMode.Repeated, TileMode.Repeated))
}

/** Horizontal hairlines: one random grey per row, with a little grain along it. */
private val brushed by lazy {
    val r = Random(3)
    val rows = IntArray(96) { r.nextInt(0, 22) }
    ShaderBrush(ImageShader(bitmap(256, 96) { _, y -> val a = (rows[y] + r.nextInt(0, 8)); (a shl 24) or (if (rows[y] % 2 == 0) 0xFFFFFF else 0x000000) }, TileMode.Repeated, TileMode.Repeated))
}

private val metalGradient = Brush.verticalGradient(
    0f to Color(0xFFF1F2F4), .45f to Color(0xFFC9CBCF), .55f to Color(0xFFB3B6BB), 1f to Color(0xFFD7D9DC),
)
val AmberGloss = Brush.verticalGradient(
    0f to Color(0xFFFFD27A), .48f to Color(0xFFFFA526), .52f to Color(0xFFF08A0C), 1f to Color(0xFFFFB03A),
)
private val lcdGradient = Brush.verticalGradient(listOf(Color(0xFF1B1408), Color(0xFF2B2010)))
private val insetGradient = Brush.verticalGradient(listOf(Color(0xFF15120F), Color(0xFF221E1A)))

/** Full-screen leather: warm vignette plus grain. */
fun Modifier.leather(): Modifier = drawBehind {
    drawRect(Brush.radialGradient(listOf(LeatherHi, LeatherLo), center = Offset(size.width / 2, 0f), radius = size.maxDimension * .75f))
    drawRect(noise)
}

/** Brushed aluminium plate: gradient, hairlines, a light top edge and a dark outline. */
fun Modifier.metal(shape: Shape, elevation: Dp = 6.dp): Modifier =
    shadow(elevation, shape).clip(shape).background(metalGradient).background(brushed)
        .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFF4A4C50))), shape)

/** Recessed well (search fields, LCDs): dark gradient with a shadow along the top inner edge. */
fun Modifier.inset(shape: Shape, brush: Brush = insetGradient): Modifier =
    clip(shape).background(brush).drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent), endY = 7.dp.toPx()))
    }.border(1.dp, Brush.verticalGradient(listOf(Color.Black, Color.White.copy(alpha = .10f))), shape)

/** Amber backlit LCD. */
fun Modifier.lcd(shape: Shape): Modifier = inset(shape, lcdGradient)

/** Glossy amber candy button. */
fun Modifier.glossAmber(shape: Shape): Modifier =
    shadow(8.dp, shape, spotColor = Color(0xFFFF9A1A)).clip(shape).background(AmberGloss)
        .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFFFF0C8), Color(0xFF7A3E00))), shape)

/** Domed dark knob (round icon buttons). */
fun Modifier.knob(): Modifier =
    shadow(6.dp, CircleShape).clip(CircleShape)
        .drawBehind { drawRect(Brush.radialGradient(listOf(Color(0xFF5A534C), Color(0xFF2A2622)), center = Offset(size.width / 2, size.height * .3f), radius = size.minDimension * .7f)) }
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .25f), Color.Black)), CircleShape)

/** Polished chrome button for the transport console. */
fun Modifier.chrome(): Modifier =
    shadow(4.dp, CircleShape).clip(CircleShape)
        .drawBehind { drawRect(Brush.radialGradient(listOf(Color.White, Color(0xFFD6D8DB), Color(0xFFB9BCC0)), center = Offset(size.width / 2, size.height * .3f), radius = size.minDimension * .75f)) }
        .border(1.dp, Color(0xFF6A6C70), CircleShape)

/** Stitching along the edge, inset by 7dp. */
fun Modifier.stitched(radius: Dp = 22.dp): Modifier = drawWithContent {
    drawContent()
    val i = 7.dp.toPx()
    drawRoundRect(
        Color(0x47F0DCBE), Offset(i, i), size.copy(width = size.width - 2 * i, height = size.height - 2 * i), CornerRadius(radius.toPx()),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Gloss reflection on top of artwork: light diagonal band and a bright top edge. */
fun Modifier.glossOverlay(): Modifier = drawWithContent {
    drawContent()
    drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = .28f), .42f to Color.White.copy(alpha = .06f), .43f to Color.Transparent, start = Offset.Zero, end = Offset(size.width * .7f, size.height)))
    drawRect(Color.White.copy(alpha = .35f), size = size.copy(height = 1.dp.toPx()))
}

/** Letterpress text: dark shadow under light text on leather, white shadow under dark text on metal. */
val Engraved = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .9f), Offset(0f, -2f), 0f))
val OnMetal = TextStyle(shadow = Shadow(Color.White.copy(alpha = .8f), Offset(0f, 2f), 0f))
fun glow(c: Color = Amber) = TextStyle(shadow = Shadow(c.copy(alpha = .8f), Offset.Zero, 14f))

/** Vinyl record that spins while playing. */
@Composable
fun Vinyl(playing: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "vinyl")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "spin")
    // keeps the angle where it stopped instead of jumping back to 0 on pause
    var frozen by remember { mutableFloatStateOf(0f) }
    val angle = if (playing) spin.also { frozen = it } else frozen
    Canvas(modifier.shadow(14.dp, CircleShape).clip(CircleShape)) {
        val c = center; val r = size.minDimension / 2
        drawCircle(Color(0xFF0A0A0A), r, c)
        var g = r * .97f
        while (g > r * .36f) { drawCircle(Color(0xFF242424), g, c, style = Stroke(.8f)); g -= 3.2f }
        drawCircle(Color(0xFF050505), r * .70f, c, style = Stroke(2f))
        rotate(angle, c) {
            drawCircle(Amber, r * .34f, c)
            drawArc(Color(0xFFE8961F), 0f, 40f, true, Offset(c.x - r * .34f, c.y - r * .34f), androidx.compose.ui.geometry.Size(r * .68f, r * .68f))
        }
        drawCircle(Color(0xFF111111), r * .04f, c)
        // still reflection
        drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = .13f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = .13f), Color.Transparent, Color.Transparent), c), r, c)
    }
}
