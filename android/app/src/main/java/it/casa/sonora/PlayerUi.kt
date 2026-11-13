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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

@Composable
fun PlayPauseIcon(modifier: Modifier, small: Boolean) {
    val playing = Playback.isPlaying
    Box(
        modifier.clip(CircleShape).background(if (small) Color.Transparent else Color.White).clickable { Playback.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (Playback.isBuffering) CircularProgressIndicator(
            Modifier.size(if (small) 22.dp else 34.dp), color = if (small) Color.White else Bg, strokeWidth = 2.5.dp,
        )
        else AnimatedContent(playing, label = "pp", transitionSpec = { scaleIn(tween(180)) + fadeIn() togetherWith scaleOut(tween(120)) + fadeOut() }) { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) "Pausa" else "Riproduci",
                tint = if (small) Color.White else Bg, modifier = Modifier.size(if (small) 30.dp else 42.dp),
            )
        }
    }
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
    val artScale by animateFloatAsState(if (Playback.isPlaying) 1f else .86f, spring(dampingRatio = .55f, stiffness = 260f), label = "art")

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = dragY.value; alpha = 1f - (dragY.value / 2400f).coerceIn(0f, .4f) }
            .background(Bg).background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = .55f), Bg)))
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
                    Text("IN RIPRODUZIONE", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = .7f))
                    Text(t.album ?: t.artist, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                Artwork(
                    cur.thumb,
                    Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = artScale; scaleY = artScale }
                        .shadow(36.dp, RoundedCornerShape(22.dp), spotColor = Color.Black),
                    RoundedCornerShape(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Marquee(t.title, size = 22, weight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    Marquee(t.artist, size = 16, color = Color.White.copy(alpha = .7f))
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
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .2f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmtMs(if (dragging) (dragValue * dur).toLong() else pos), fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                Spacer(Modifier.weight(1f))
                Text(if (dur > 0) fmtMs(dur) else "--:--", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Playback.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Casuale", tint = if (Playback.shuffle) Accent else Color.White.copy(alpha = .75f))
                }
                IconButton(onClick = { Playback.previous() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Precedente", tint = Color.White, modifier = Modifier.size(42.dp))
                }
                PlayPauseIcon(Modifier.size(76.dp).shadow(20.dp, CircleShape), small = false)
                IconButton(onClick = { Playback.next() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Successivo", tint = Color.White, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { Playback.cycleRepeat() }) {
                    Icon(
                        if (Playback.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ripeti",
                        tint = if (Playback.repeat != Player.REPEAT_MODE_OFF) Accent else Color.White.copy(alpha = .75f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(20.dp)).clickable { showQueue = true }
                    .background(Color.White.copy(alpha = .08f)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prossimi brani", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = CardHi) {
        val q = remember(Playback.queueVersion) { Playback.upNext() }
        Text("Prossimi brani", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        if (q.isEmpty()) Text("La coda è vuota", color = Dim, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(q, key = { "${it.first}-${it.second.id}" }) { (i, tr) ->
                TrackRow(tr, onClick = { Playback.jumpTo(i) }, onMore = { Ui.menu = MenuReq(tr) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
