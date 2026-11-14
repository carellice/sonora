package it.casa.sonora

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Horizontal swipe: left = next track, right = previous track. The content follows the finger and springs back. */
@Composable
fun Modifier.swipeTracks(): Modifier {
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    return graphicsLayer { translationX = dx.value; alpha = 1f - (kotlin.math.abs(dx.value) / 1200f).coerceIn(0f, .5f) }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (dx.value < -160f) Playback.next() else if (dx.value > 160f) Playback.previousTrack()
                    scope.launch { dx.animateTo(0f, spring()) }
                },
                onDragCancel = { scope.launch { dx.animateTo(0f, spring()) } },
            ) { _, d -> scope.launch { dx.snapTo(dx.value + d) } }
        }
}

/** Current position, polled while visible. */
@Composable
fun rememberPosition(): Pair<Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Playback.controller) {
        while (true) {
            Playback.controller?.let { pos = it.currentPosition; dur = it.duration.coerceAtLeast(0) }
            delay(250)
        }
    }
    return pos to dur
}

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val t = Playback.current ?: return
    val tint = rememberArtColor(t.thumb)
    val (pos, dur) = rememberPosition()
    val src = remember { MutableInteractionSource() }
    if (Skin.skeu) return SkeuMiniPlayer(t, pos, dur, src, onOpen)
    if (Skin.metro) return MetroMiniPlayer(t, pos, dur, src, onOpen)
    Box(
        Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth().pressScale(src)
            .shadow(16.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(tint, Color(0xFF1A1824))))
            .clickable(interactionSource = src, indication = ripple(), onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) onOpen() } },
    ) {
        Row(Modifier.swipeTracks().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(t, label = "miniart", transitionSpec = { fadeIn(tween(300)) + scaleIn(initialScale = .8f) togetherWith fadeOut(tween(200)) }) {
                Artwork(it.thumb, Modifier.size(44.dp), RoundedCornerShape(10.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Marquee(t.title, size = 14, weight = FontWeight.SemiBold)
                Text(t.artist, color = Color.White.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1)
            }
            PlayPauseIcon(Modifier.size(44.dp), small = true)
            IconButton(onClick = { Playback.next() }) { Icon(Icons.Rounded.SkipNext, "Successivo", tint = Color.White) }
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp).fillMaxWidth().height(2.dp)
                .clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = .15f)),
        ) {
            Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).height(2.dp).background(Color.White))
        }
    }
}

/** Skeuomorphism: brushed-metal faceplate with an amber LCD showing the song. */
@Composable
private fun SkeuMiniPlayer(t: Track, pos: Long, dur: Long, src: MutableInteractionSource, onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth().pressScale(src)
            .metal(RoundedCornerShape(14.dp), 14.dp)
            .clickable(interactionSource = src, indication = ripple(), onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) onOpen() } }
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(t, label = "miniart", transitionSpec = { fadeIn(tween(300)) + scaleIn(initialScale = .8f) togetherWith fadeOut(tween(200)) }) {
            Artwork(it.thumb, Modifier.size(44.dp).shadow(3.dp, RoundedCornerShape(4.dp)), RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).height(44.dp).lcd(RoundedCornerShape(6.dp)).swipeTracks().padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
            Marquee(t.title, size = 14, weight = FontWeight.Bold, color = LcdText, style = glow(), mono = true)
            Text(t.artist, color = LcdText.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(LcdText.copy(alpha = .15f))) {
                Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).height(2.dp).background(LcdText))
            }
        }
        PlayPauseIcon(Modifier.size(44.dp), small = true)
        IconButton(onClick = { Playback.next() }) { Icon(Icons.Rounded.SkipNext, "Successivo", tint = MetalText) }
    }
}

/** Modern UI: a flat chrome strip, square cover flush left, thin accent progress line on top. */
@Composable
private fun MetroMiniPlayer(t: Track, pos: Long, dur: Long, src: MutableInteractionSource, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF2B2B2B))
            .clickable(interactionSource = src, indication = ripple(), onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) onOpen() } },
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).height(3.dp).background(MetroAccent))
        }
        Row(Modifier.swipeTracks(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(t, label = "miniart", transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }) {
                Artwork(it.thumb, Modifier.size(58.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Marquee(t.title.lowercase(), size = 17)
                Text(t.artist.lowercase(), color = MetroSubtle, fontSize = 13.sp, maxLines = 1)
            }
            PlayPauseIcon(Modifier.size(40.dp).ring(), small = true)
            IconButton(onClick = { Playback.next() }) { Icon(Icons.Rounded.SkipNext, "Successivo", tint = Color.White) }
        }
    }
}

@Composable
fun PlayPauseIcon(modifier: Modifier, small: Boolean) {
    val playing = Playback.isPlaying
    val skeu = Skin.skeu
    // skeuomorphism: dark icon on the metal faceplate (small), amber-lit knob in a metal ring (big)
    val fg = when { skeu && small -> MetalText; skeu -> Amber; small || Skin.metro -> Color.White; else -> Bg }
    Box(
        modifier.then(
            when {
                skeu && !small -> Modifier.clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFE0E2E5), Color(0xFF6A6C70)))).padding(4.dp)
                    .clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF4A443E), Color(0xFF1A1714))))
                Skin.metro && !small -> Modifier.ring()
                else -> Modifier.clip(CircleShape).background(if (small) Color.Transparent else Color.White)
            },
        ).clickable { Playback.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (Playback.isBuffering) CircularProgressIndicator(Modifier.size(if (small) 22.dp else 34.dp), color = fg, strokeWidth = 2.5.dp)
        else AnimatedContent(playing, label = "pp", transitionSpec = { scaleIn(tween(180)) + fadeIn() togetherWith scaleOut(tween(120)) + fadeOut() }) { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) "Pausa" else "Riproduci",
                tint = fg, modifier = Modifier.size(if (small) (if (Skin.metro) 24.dp else 30.dp) else 42.dp),
            )
        }
    }
}

/** Transport button: chrome cap in skeuomorphism, plain icon otherwise. */
@Composable
private fun Transport(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Int, iconSize: Int, tint: Color, onClick: () -> Unit) {
    if (Skin.skeu) Box(Modifier.size((size - 6).dp).chrome().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size((iconSize * .8f).dp))
    }
    else if (Skin.metro) Box(Modifier.size((size - 8).dp).ring(tint).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size((iconSize * .62f).dp))
    }
    else IconButton(onClick = onClick, modifier = Modifier.size(size.dp)) { Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(onClose: () -> Unit) {
    val t = Playback.current
    LaunchedEffect(t == null) { if (t == null) onClose() }
    if (t == null) return
    val tint = rememberArtColor(t.thumb)
    val (pos, dur) = rememberPosition()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val skeu = Skin.skeu
    // skeuomorphism: the sleeve stays put and the record slides out instead
    val metro = Skin.metro
    val artScale by animateFloatAsState(if (Playback.isPlaying || skeu || metro) 1f else .86f, spring(dampingRatio = .55f, stiffness = 260f), label = "art")
    val vinylOut by animateFloatAsState(if (Playback.isPlaying) 1f else 0f, spring(dampingRatio = .6f, stiffness = 120f), label = "vinyl")
    val lightText = if (skeu) Color(0xFFCFC3B3) else Color.White.copy(alpha = .7f)

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = dragY.value; alpha = 1f - (dragY.value / 2400f).coerceIn(0f, .4f) }
            .background(Bg)
            .then(
                if (skeu) Modifier.leather().background(Brush.verticalGradient(0f to tint.copy(alpha = .35f), .6f to Color.Transparent))
                else if (metro) Modifier
                else Modifier.background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = .55f), Bg))),
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { scope.launch { if (dragY.value > 260f) onClose() else dragY.animateTo(0f, spring()) } },
                    onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                ) { _, dy -> scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) } }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Chiudi", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (skeu) Text("IN RIPRODUZIONE", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Amber, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, style = glow())
                    else if (metro) MetroTitle("Sonora · In riproduzione")
                    else Text("IN RIPRODUZIONE", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = .7f))
                    if (!metro) Text(t.album ?: t.artist, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                IconButton(onClick = { Ui.menu = MenuReq(t) }) { Icon(Icons.Rounded.MoreVert, "Opzioni", tint = Color.White) }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                t, label = "art", modifier = Modifier.fillMaxWidth().swipeTracks(),
                transitionSpec = {
                    (slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(380))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(250)))
                },
            ) { cur ->
                if (skeu) BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // album sleeve on the left, the record peeking out on the right
                    val sleeve = maxWidth * .8f
                    val disc = sleeve * .94f
                    // paused: hidden inside the sleeve; playing: slides out to the right edge
                    Vinyl(
                        Playback.isPlaying,
                        Modifier.padding(vertical = sleeve * .03f).size(disc)
                            .graphicsLayer { translationX = (sleeve * .03f + (maxWidth - disc - sleeve * .03f) * vinylOut).toPx() },
                    )
                    Artwork(
                        cur.thumb, Modifier.size(sleeve).shadow(24.dp, RoundedCornerShape(3.dp), spotColor = Color.Black),
                        RoundedCornerShape(3.dp),
                    )
                }
                else if (metro) Column {
                    // Windows Phone "now playing": huge lowercase artist name bleeding off the edge, flat square cover
                    Text(
                        cur.artist.lowercase(), fontSize = 54.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip, modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Artwork(cur.thumb, Modifier.fillMaxWidth().aspectRatio(1f))
                }
                else Artwork(
                    cur.thumb,
                    Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = artScale; scaleY = artScale }
                        .shadow(36.dp, RoundedCornerShape(22.dp), spotColor = Color.Black),
                    RoundedCornerShape(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.then(if (skeu) Modifier.lcd(RoundedCornerShape(12.dp)).padding(start = 14.dp, top = 8.dp, bottom = 8.dp) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (skeu) {
                        Marquee(t.title, size = 20, weight = FontWeight.Bold, color = LcdText, style = glow(), mono = true)
                        Spacer(Modifier.height(2.dp))
                        Marquee(t.artist, size = 15, color = LcdText.copy(alpha = .72f), mono = true)
                    } else if (metro) {
                        Marquee(t.title.lowercase(), size = 26, weight = FontWeight.Light)
                        Text((t.album ?: t.artist).lowercase(), fontSize = 15.sp, color = MetroSubtle, maxLines = 1)
                    } else {
                        Marquee(t.title, size = 22, weight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(2.dp))
                        Marquee(t.artist, size = 16, color = Color.White.copy(alpha = .7f))
                    }
                }
                val saved = Lib.hasSong(t.id)
                val hs by animateFloatAsState(if (saved) 1.2f else 1f, spring(dampingRatio = .3f, stiffness = 400f), label = "h")
                IconButton(onClick = { Lib.scope.launch { Lib.toggleSong(t) } }) {
                    Icon(
                        if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Libreria",
                        tint = if (saved) Accent else Color.White, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = hs; scaleY = hs },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val progress = if (dragging) dragValue else if (dur > 0) pos.toFloat() / dur else 0f
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { Playback.seek((dragValue * dur).toLong()); dragging = false },
                colors = if (skeu) SliderDefaults.colors(thumbColor = Color(0xFFE6E8EB), activeTrackColor = Amber, inactiveTrackColor = Color.Black.copy(alpha = .6f))
                else if (metro) SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MetroAccent, inactiveTrackColor = Color(0xFF333333))
                else SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .2f)),
            )
            Row(Modifier.fillMaxWidth()) {
                val ff = if (skeu) FontFamily.Monospace else null
                Text(fmtMs(if (dragging) (dragValue * dur).toLong() else pos), fontSize = 12.sp, color = lightText, fontFamily = ff)
                Spacer(Modifier.weight(1f))
                Text(if (dur > 0) fmtMs(dur) else "--:--", fontSize = 12.sp, color = lightText, fontFamily = ff)
            }
            Spacer(Modifier.height(10.dp))
            // skeuomorphism: chrome buttons on a brushed-metal console, toggles lit amber when on
            val on = if (skeu) Color(0xFFE08400) else Accent
            val off = if (skeu) MetalText else if (metro) Color(0xFF777777) else Color.White.copy(alpha = .75f)
            val main = if (skeu) MetalText else Color.White
            Row(
                Modifier.fillMaxWidth().then(if (skeu) Modifier.metal(RoundedCornerShape(18.dp), 14.dp).padding(horizontal = 12.dp, vertical = 12.dp) else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Transport(Icons.Rounded.Shuffle, "Casuale", 48, 24, if (Playback.shuffle) on else off) { Playback.toggleShuffle() }
                Transport(Icons.Rounded.SkipPrevious, "Precedente", 60, 42, main) { Playback.previous() }
                PlayPauseIcon(Modifier.size(if (skeu) 84.dp else 76.dp).then(if (metro) Modifier else Modifier.shadow(20.dp, CircleShape)), small = false)
                Transport(Icons.Rounded.SkipNext, "Successivo", 60, 42, main) { Playback.next() }
                Transport(
                    if (Playback.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ripeti", 48, 24,
                    if (Playback.repeat != Player.REPEAT_MODE_OFF) on else off,
                ) { Playback.cycleRepeat() }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally)
                    .then(
                        if (skeu) Modifier.metal(RoundedCornerShape(10.dp), 4.dp) else if (metro) Modifier.border(2.dp, Color.White)
                        else Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .08f)),
                    )
                    .clickable { showQueue = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = main, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prossimi brani".mt(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = main, style = if (skeu) OnMetal else androidx.compose.ui.text.TextStyle.Default)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = CardHi) {
        val q = remember(Playback.queueVersion) { Playback.upNext() }
        Text(
            "Prossimi brani".mt(), fontSize = if (Skin.metro) 30.sp else 20.sp, fontWeight = if (Skin.metro) FontWeight.Light else FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        if (q.isEmpty()) Text("La coda è vuota", color = Dim, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(q, key = { "${it.first}-${it.second.id}" }) { (i, tr) ->
                TrackRow(tr, onClick = { Playback.jumpTo(i) }, onMore = { Ui.menu = MenuReq(tr) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
