package it.casa.sonora

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            // Start after 1s of audio instead of 2.5s: the server sends the first MB at LAN speed.
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(50_000, 50_000, 1_000, 2_000).build())
            // A seek far into a track not yet resolved waits for yt-dlp (a few s): the 8s default timeout is too short.
            .setMediaSourceFactory(
                // DefaultDataSource: streams over HTTP, downloads from file:// (app storage) or content:// (picked folder)
                DefaultMediaSourceFactory(this).setDataSourceFactory(
                    DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory().setConnectTimeoutMs(30_000).setReadTimeoutMs(60_000)),
                ),
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // The stream URI travels in requestMetadata: controllers don't pass localConfiguration to the session.
                override fun onAddMediaItems(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { it.buildUpon().setUri(it.requestMetadata.mediaUri).build() }.toMutableList(),
                )
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** UI-side view of the player, backed by a MediaController bound to PlaybackService. */
object Playback {
    var controller by mutableStateOf<MediaController?>(null)
        private set
    var current by mutableStateOf<Track?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queueVersion by mutableIntStateOf(0)
        private set

    private var future: ListenableFuture<MediaController>? = null
    private val scope = MainScope()
    private lateinit var app: Context

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) prefetch()
        }
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(app, "Impossibile riprodurre il brano", Toast.LENGTH_SHORT).show()
            val c = controller ?: return
            if (c.hasNextMediaItem()) { c.seekToNextMediaItem(); c.prepare(); c.play() }
        }
    }

    private fun connect(ctx: Context) {
        if (future != null) return
        app = ctx.applicationContext
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        future = f
        f.addListener({
            runCatching { f.get() }.onSuccess { c -> controller = c; c.addListener(listener); sync() }
        }, ContextCompat.getMainExecutor(app))
    }

    // Activities attach/detach; the controller is released only when the last one goes away
    // (an old instance being destroyed must not cut off the new one).
    private var refs = 0
    fun attach(ctx: Context) { refs++; connect(ctx) }
    fun detach() { if (--refs <= 0) { refs = 0; release() } }

    private fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun sync() {
        val c = controller ?: return
        current = c.currentMediaItem?.track()
        isPlaying = c.isPlaying
        isBuffering = c.playbackState == Player.STATE_BUFFERING
        shuffle = c.shuffleModeEnabled
        repeat = c.repeatMode
        queueVersion++
    }

    /** Asks the server to resolve the next tracks now, so they start without the resolve delay. */
    private val prepared = mutableSetOf<String>()
    private fun prefetch() {
        upNext().take(2).map { it.second.id }.filter { prepared.add(it) }.forEach { id ->
            scope.launch { runCatching { Api.prepare(id) }.onFailure { prepared.remove(id) } }
        }
        if (prepared.size > 500) prepared.clear()
    }

    private fun item(t: Track): MediaItem {
        val uri = Downloads.uriFor(t)
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
                    .setArtworkUri(t.thumb?.let(Uri::parse))
                    .setExtras(Bundle().apply { putString("t", t.json().toString()) })
                    .build(),
            )
            .build()
    }

    private fun MediaItem.track(): Track? = mediaMetadata.extras?.getString("t")?.let { Track.from(JSONObject(it)) }

    fun play(list: List<Track>, index: Int = 0, shuffled: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        // "Casuale" plays a shuffled copy in order: with shuffle mode and a random start index the start
        // could be the last of ExoPlayer's shuffle order, leaving no next track.
        c.shuffleModeEnabled = false
        c.setMediaItems((if (shuffled) list.shuffled() else list).map(::item), if (shuffled) 0 else index, 0)
        c.prepare()
        c.play()
    }

    /** Plays one song, then fills the queue with YouTube Music's radio for it. */
    fun playRadio(t: Track) {
        play(listOf(t))
        scope.launch {
            runCatching { Api.radio(t.id) }.onSuccess { r ->
                val c = controller ?: return@onSuccess
                if (c.mediaItemCount == 1 && c.currentMediaItem?.mediaId == t.id) c.addMediaItems(r.map(::item))
            }
        }
    }

    /** Artist's "Casuale": its popular songs shuffled at once, then all its other songs, looping forever. */
    fun shuffleAll(first: List<Track>, more: suspend () -> List<Track>) {
        play(first, shuffled = true)
        controller?.repeatMode = Player.REPEAT_MODE_ALL
        scope.launch {
            runCatching { more() }.onSuccess { all ->
                val c = controller ?: return@onSuccess
                val have = first.map { it.id }.toSet()
                // only if the queue is still this one
                if (c.mediaItemCount == first.size && c.currentMediaItem?.mediaId in have) c.addMediaItems(all.filter { it.id !in have }.shuffled().map(::item))
            }
        }
    }

    fun playNext(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(t))
        c.addMediaItem(c.currentMediaItemIndex + 1, item(t))
    }

    fun enqueue(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(t))
        c.addMediaItem(item(t))
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seek(ms: Long) = controller?.seekTo(ms)
    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Upcoming items in play order (respects shuffle): index in the timeline to Track. */
    fun upNext(): List<Pair<Int, Track>> {
        val c = controller ?: return emptyList()
        val tl = c.currentTimeline
        if (tl.isEmpty) return emptyList()
        val out = mutableListOf<Pair<Int, Track>>()
        var i = tl.getNextWindowIndex(c.currentMediaItemIndex, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && out.size < 200) {
            c.getMediaItemAt(i).track()?.let { out += i to it }
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }

    fun jumpTo(index: Int) = controller?.let { it.seekTo(index, 0); it.play() }
}
