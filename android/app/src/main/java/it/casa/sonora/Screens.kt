package it.casa.sonora

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One track menu sheet for the whole app; screens just set the request. */
data class MenuReq(val track: Track, val localPlaylistId: String? = null)

object Ui {
    var menu by mutableStateOf<MenuReq?>(null)
    var addToPlaylist by mutableStateOf<Track?>(null)
}

private fun String.has(q: String) = contains(q.trim(), ignoreCase = true)

// ---------------- Setup ----------------

@Composable
fun SetupScreen(initial: String, onDone: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(
        Modifier.fillMaxSize().background(if (Skin.metro || Skin.nothing) Brush.verticalGradient(listOf(Bg, Bg)) else Brush.verticalGradient(listOf(Accent2.copy(alpha = .35f), Bg, Bg)))
            .then(if (Skin.nothing) Modifier.dotGrid() else Modifier)
            .imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(shown, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 6 }) {
            Column(Modifier.statusBarsPadding().padding(28.dp).padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val logo = if (Skin.metro) androidx.compose.ui.graphics.RectangleShape else CircleShape
                if (Skin.nothing) NdHero("Sonora", size = 64.sp)
                else Box(Modifier.size(96.dp).shadow(if (Skin.metro) 0.dp else 24.dp, logo, spotColor = Accent).clip(logo).background(AccentBrush), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Benvenuto in Sonora".mt(), fontSize = if (Skin.metro) 34.sp else 28.sp, fontWeight = if (Skin.metro) FontWeight.Light else if (Skin.nothing) FontWeight.Normal else FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Inserisci l'indirizzo HTTPS del proxy Tailscale del tuo server di casa.",
                    color = Dim, textAlign = TextAlign.Center, fontSize = 15.sp,
                )
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    url, { url = it; error = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Indirizzo server") },
                    placeholder = { Text("https://casaos.tailxxxx.ts.net:10008") },
                    shape = corner(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent, cursorColor = Accent),
                    isError = error != null,
                )
                AnimatedVisibility(error != null) {
                    Text(error ?: "", color = if (Skin.nothing) NdRed else Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(24.dp))
                if (busy) Spinner()
                else GradientButton("Connetti", Icons.Rounded.PlayArrow, Modifier.fillMaxWidth()) {
                    val u = Api.normalize(url)
                    busy = true
                    Lib.scope.launch {
                        val ok = runCatching { Api.health(u) }.getOrDefault(false)
                        busy = false
                        if (ok) onDone(u) else error = "Server non raggiungibile. Controlla l'indirizzo e che Tailscale sia attivo."
                    }
                }
            }
        }
    }
}

// ---------------- Library ----------------

@Composable
fun SegmentedTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (Skin.metro) return PivotHeaders(tabs, selected, onSelect, modifier)
    if (Skin.nothing) return NdSegmented(tabs, selected, onSelect, modifier)
    // skeuomorphism: a brushed-metal rocker whose active key is pressed in and lit amber
    val skeu = Skin.skeu
    val r = if (skeu) 8.dp else 17.dp
    BoxWithConstraints(
        modifier.fillMaxWidth().height(42.dp)
            .then(if (skeu) Modifier.metal(RoundedCornerShape(10.dp), 3.dp) else Modifier.clip(RoundedCornerShape(21.dp)).background(Card))
            .padding(4.dp),
    ) {
        val w = maxWidth / tabs.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = .75f, stiffness = 500f), label = "tab")
        Box(Modifier.offset(x = x).width(w).height(34.dp).then(if (skeu) Modifier.inset(RoundedCornerShape(r)) else Modifier.clip(RoundedCornerShape(r)).background(AccentBrush)))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, t ->
                val c by animateColorAsState(if (i == selected) (if (skeu) Amber else Color.White) else if (skeu) MetalText else Dim, label = "tabc")
                Box(
                    Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(r)).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t, color = c, fontWeight = FontWeight.SemiBold, fontSize = if (tabs.size > 4 || tabs.any { it.length > 10 }) 12.sp else 13.sp, maxLines = 1,
                        style = if (!skeu) androidx.compose.ui.text.TextStyle.Default else if (i == selected) glow() else OnMetal,
                    )
                }
            }
        }
    }
}

/** Nothing segmented control: outlined pill, the chosen segment inverted (white, black text), mechanical slide. */
@Composable
private fun NdSegmented(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(40.dp).ndOutline().padding(3.dp)) {
        val w = maxWidth / tabs.size
        val x by animateDpAsState(w * selected, tween(200, easing = androidx.compose.animation.core.CubicBezierEasing(.25f, .1f, .25f, 1f)), label = "ndtab")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(Color.White))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, t ->
                val c by animateColorAsState(if (i == selected) Color.Black else NdSecondary, tween(200), label = "ndtabc")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    NdLabel(t, color = c, size = if (tabs.size > 4) 10.sp else 11.sp)
                }
            }
        }
    }
}

/** The loading indicator of the current skin. */
@Composable
fun Spinner() = if (Skin.metro) MetroDots() else if (Skin.nothing) NdLoader() else CircularProgressIndicator(color = Accent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavController, onSettings: () -> Unit) {
    val dlCount = Downloads.queue.size
    val tabs = listOf("Brani", "Album", "Artisti", "Playlist", if (dlCount > 0) "↓ $dlCount" else "Scaricati")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    var newPlaylist by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val lib = Lib.data

    PullToRefreshBox(
        isRefreshing = Lib.loading,
        onRefresh = { Lib.scope.launch { Lib.refresh().onFailure { Toast.makeText(ctx, "Server non raggiungibile", Toast.LENGTH_SHORT).show() } } },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (Skin.metro) MetroTitle("Sonora · Libreria", Modifier.weight(1f))
                else if (Skin.nothing) NdHero("Libreria", Modifier.weight(1f).padding(top = 8.dp))
                else Text("Libreria", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), style = skeuText())
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Impostazioni", tint = Dim) }
            }
            val pick = { i: Int -> tab = i; filter = "" }
            SegmentedTabs(tabs, tab, pick, if (Skin.metro) Modifier.padding(bottom = 8.dp) else Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            SearchField(filter, { filter = it }, "Cerca in ${if (tab == 4) "scaricati" else tabs[tab].lowercase()}", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                tab, label = "libtab", modifier = Modifier.fillMaxSize().pivotSwipe(tabs.size, tab, pick),
                transitionSpec = {
                    if (Skin.nothing) return@AnimatedContent fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 5 * dir } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 5 * dir } + fadeOut(tween(160)))
                },
            ) { t ->
                when (t) {
                    0 -> {
                        val songs = lib.songs.filter { filter.isBlank() || it.title.has(filter) || it.artist.has(filter) || (it.album ?: "").has(filter) }
                        if (lib.songs.isEmpty()) EmptyState(Icons.Rounded.MusicNote, "Nessun brano salvato", "Cerca un brano e tocca il cuore per aggiungerlo alla libreria.")
                        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            item { PlayShuffleRow(songs) }
                            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                                TrackRow(s, onClick = { Playback.play(songs, i) }, onMore = { Ui.menu = MenuReq(s) })
                            }
                        }
                    }
                    1 -> {
                        val albums = lib.albums.filter { filter.isBlank() || it.title.has(filter) || it.artist.has(filter) }
                        if (lib.albums.isEmpty()) EmptyState(Icons.Rounded.Album, "Nessun album", "Gli album dei brani salvati e quelli che segui compaiono qui.")
                        else LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(10.dp)) {
                            items(albums, key = { it.id }) { a ->
                                AlbumCard(a.title, a.artist, a.thumb) { nav.navigate("album/${a.id}") }
                            }
                        }
                    }
                    2 -> {
                        val artists = lib.artists.filter { filter.isBlank() || it.name.has(filter) }
                        if (lib.artists.isEmpty()) EmptyState(Icons.Rounded.Person, "Nessun artista", "Gli artisti dei brani salvati e quelli che segui compaiono qui.")
                        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(artists, key = { it.id }) { a -> ArtistRow(a) { nav.navigate("artist/${a.id}") } }
                        }
                    }
                    4 -> {
                        val all = Downloads.saved
                        val songs = all.filter { filter.isBlank() || it.title.has(filter) || it.artist.has(filter) || (it.album ?: "").has(filter) }
                        val queue = Downloads.queue
                        if (queue.isNotEmpty()) LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            item { DownloadQueueHeader() }
                            items(queue, key = { "q-${it.id}" }) { t -> DownloadingRow(t) }
                            if (all.isNotEmpty()) item { SectionTitle("Scaricati") }
                            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                                TrackRow(s, onClick = { Playback.play(songs, i) }, onMore = { Ui.menu = MenuReq(s) })
                            }
                        }
                        else if (all.isEmpty()) EmptyState(Icons.Rounded.DownloadForOffline, "Nessun brano scaricato", "Dal menu ⋮ di un brano o con il pulsante su album e playlist: li ascolti anche senza connessione.")
                        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            item {
                                val mb = remember(all.size) { Downloads.sizeBytes() / 1_048_576 }
                                val info = "${if (all.size == 1) "1 brano" else "${all.size} brani"} • $mb MB • ${Downloads.folderName() ?: "memoria dell'app"}"
                                if (Skin.nothing) NdLabel(info, Modifier.padding(start = 20.dp, top = 4.dp))
                                else Text(info, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
                            }
                            item { PlayShuffleRow(songs) }
                            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                                TrackRow(s, onClick = { Playback.play(songs, i) }, onMore = { Ui.menu = MenuReq(s) })
                            }
                        }
                    }
                    else -> {
                        val pls = lib.playlists.filter { filter.isBlank() || it.name.has(filter) }
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().listRow()
                                        .clickable { newPlaylist = true }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(56.dp).then(if (Skin.skeu) Modifier.glossAmber(RoundedCornerShape(8.dp)) else Modifier.clip(corner(12.dp)).background(AccentBrush)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.Add, null, tint = if (Skin.skeu) Color(0xFF4A2A00) else Color.White, modifier = Modifier.size(30.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text("Nuova playlist".mt(), fontWeight = if (Skin.metro) FontWeight.Light else if (Skin.nothing) FontWeight.Normal else FontWeight.SemiBold, fontSize = if (Skin.metro) 22.sp else 16.sp, style = skeuText())
                                }
                            }
                            items(pls, key = { it.id }) { p ->
                                Row(
                                    Modifier.fillMaxWidth().listRow()
                                        .clickable { nav.navigate("local/${p.id}") }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Mosaic(p.tracks.mapNotNull { it.thumb }, Modifier.size(56.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            p.name, fontWeight = if (Skin.metro) FontWeight.Light else if (Skin.nothing) FontWeight.Normal else FontWeight.SemiBold, fontSize = if (Skin.metro) 22.sp else 16.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = skeuText(),
                                        )
                                        val n = if (p.tracks.size == 1) "1 brano" else "${p.tracks.size} brani"
                                        if (Skin.nothing) NdLabel(n, Modifier.padding(top = 2.dp), size = 10.sp) else Text(n, color = Dim, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (newPlaylist) NewPlaylistDialog(onDismiss = { newPlaylist = false }) { name ->
        newPlaylist = false
        Lib.scope.launch { Lib.createPlaylist(name) }
    }
}

/** "In download: 3 brani · 42%" with the overall bar and "Annulla tutti". */
@Composable
fun DownloadQueueHeader() {
    val p by androidx.compose.animation.core.animateFloatAsState(Downloads.queueProgress(), label = "dlq")
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "In download: ${Downloads.queue.size} ${if (Downloads.queue.size == 1) "brano" else "brani"} • ${(p * 100).toInt()}%",
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { Downloads.cancelAll() }) { Text("Annulla tutti", color = Accent) }
        }
        if (Skin.nothing) SegmentedBar(p, Modifier.padding(top = 6.dp))
        else LinearProgressIndicator(
            progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Accent, trackColor = Card, drawStopIndicator = {},
        )
    }
}

/** A song being downloaded: its own progress bar and a button to stop it. */
@Composable
fun DownloadingRow(t: Track) {
    val raw = Downloads.active[t.id] ?: 0f
    val p by androidx.compose.animation.core.animateFloatAsState(raw, label = "dl")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(t.thumb, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (Skin.nothing) { if (raw == 0f) NdLoader() else SegmentedBar(p, height = 6.dp) }
            else if (raw == 0f) LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Accent2, trackColor = Card)
            else LinearProgressIndicator(
                progress = { p }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Accent, trackColor = Card, drawStopIndicator = {},
            )
            Text(if (raw == 0f) "In attesa del server…" else "${(raw * 100).toInt()}%", color = Dim, fontSize = 12.sp)
        }
        IconButton(onClick = { Downloads.cancel(listOf(t)) }) { Icon(Icons.Rounded.Close, "Annulla download", tint = Dim) }
    }
}

@Composable
fun PlayShuffleRow(tracks: List<Track>, onShuffle: () -> Unit = { Playback.play(tracks, shuffled = true) }) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientButton("Riproduci", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { Playback.play(tracks) }
        SoftButton("Casuale", Icons.Rounded.Shuffle, Modifier.weight(1f)) { onShuffle() }
    }
}

@Composable
fun Mosaic(thumbs: List<String>, modifier: Modifier) {
    val t = thumbs.distinct()
    if (t.size < 4) Artwork(t.firstOrNull(), modifier, RoundedCornerShape(12.dp), Icons.AutoMirrored.Rounded.QueueMusic)
    else Column(modifier.clip(corner(12.dp))) {
        Row(Modifier.weight(1f)) { Artwork(t[0], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp)); Artwork(t[1], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp)) }
        Row(Modifier.weight(1f)) { Artwork(t[2], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp)); Artwork(t[3], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp)) }
    }
}

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) = NewNameDialog("Nuova playlist", "Crea", onDismiss, onCreate)

@Composable
fun NewNameDialog(title: String, action: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardHi,
        title = { Text(title.mt()) },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true, placeholder = { Text("Nome") }, shape = corner(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
            )
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text(action, color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = Dim) } },
    )
}

// ---------------- Search ----------------

@Composable
fun SearchScreen(nav: NavController) {
    val types = listOf("Brani", "Album", "Artisti", "Playlist")
    var q by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<Any>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(q, type) {
        if (q.isBlank()) { results = emptyList(); loading = false; return@LaunchedEffect }
        delay(350) // debounce typing
        loading = true; error = false
        runCatching {
            when (type) {
                0 -> Api.searchSongs(q)
                1 -> Api.searchAlbums(q)
                2 -> Api.searchArtists(q)
                else -> Api.searchPlaylists(q)
            }
        }.onSuccess { results = it }.onFailure { error = true }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (Skin.metro) {
            MetroTitle("Sonora", Modifier.padding(start = 20.dp, top = 22.dp))
            Text("cerca", fontSize = 56.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        } else if (Skin.nothing) NdHero("Cerca", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 16.dp))
        else Text("Cerca", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, style = skeuText(), modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 10.dp))
        SearchField(q, { q = it }, "Artisti, brani, album…", Modifier.padding(horizontal = 16.dp), big = true)
        SegmentedTabs(types, type, { type = it }, if (Skin.metro) Modifier.padding(top = 10.dp) else Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Box(Modifier.fillMaxWidth().height(if (Skin.nothing) 12.dp else 4.dp)) {
            if (loading) { if (Skin.metro) MetroDots() else if (Skin.nothing) NdLoader(Modifier.padding(start = 20.dp)) else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Color.Transparent) }
        }
        Box(Modifier.weight(1f).pivotSwipe(types.size, type) { type = it }) { when {
            q.isBlank() -> EmptyState(Icons.Rounded.Search, "Trova la tua musica", "Cerca tra milioni di brani, album, artisti e playlist di YouTube Music.")
            error -> EmptyState(Icons.Rounded.WifiOff, "Ricerca non riuscita", "Controlla la connessione al server e riprova.")
            !loading && results.isEmpty() -> EmptyState(Icons.Rounded.Search, "Nessun risultato", "Prova con parole diverse.")
            else -> AnimatedContent(results, label = "results", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { list ->
                when (list.firstOrNull()) {
                    is Album -> LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(10.dp)) {
                        items(list.filterIsInstance<Album>(), key = { it.id }) { a ->
                            AlbumCard(a.title, listOfNotNull(a.artist, a.year).joinToString(" • "), a.thumb) { nav.navigate("album/${a.id}") }
                        }
                    }
                    is Playlist -> LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(10.dp)) {
                        items(list.filterIsInstance<Playlist>(), key = { it.id }) { p ->
                            AlbumCard(p.title, p.author, p.thumb) { nav.navigate("playlist/${p.id}") }
                        }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(list, key = { (it as? Track)?.id ?: (it as Artist).id }) { r ->
                            when (r) {
                                is Track -> TrackRow(r, onClick = { Playback.playRadio(r) }, onMore = { Ui.menu = MenuReq(r) })
                                is Artist -> ArtistRow(r) { nav.navigate("artist/${r.id}") }
                            }
                        }
                    }
                }
            }
        } }
    }
}

// ---------------- Collections (album / playlist) ----------------

@Composable
fun BackButton(nav: NavController, modifier: Modifier = Modifier) {
    IconButton(
        onClick = { nav.popBackStack() },
        modifier = modifier.statusBarsPadding().padding(8.dp).size(40.dp).then(
            if (Skin.skeu) Modifier.knob() else if (Skin.metro) Modifier.ring().background(Color.Black.copy(alpha = .5f))
            else if (Skin.nothing) Modifier.clip(CircleShape).background(NdSurface).border(1.dp, NdBorder, CircleShape)
            else Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .35f)),
        ),
    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", tint = Color.White) }
}

@Composable
fun <T> Loader(key: Any, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var data by remember(key) { mutableStateOf<T?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, attempt) {
        failed = false
        runCatching { load() }.onSuccess { data = it }.onFailure { failed = true }
    }
    val d = data
    when {
        d != null -> content(d)
        failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Rounded.WifiOff, "Caricamento non riuscito", "Controlla la connessione al server.")
                TextButton(onClick = { attempt++ }) { Text("Riprova", color = Accent) }
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
    }
}

/** Shared layout for albums and playlists: tinted header, big artwork, actions, track list. */
@Composable
fun CollectionScreen(
    nav: NavController, title: String, subtitle: String, thumb: String?, tracks: List<Track>,
    numbered: Boolean, onSubtitle: (() -> Unit)? = null,
    art: @Composable (Modifier) -> Unit = { Artwork(thumb, it, corner(if (Skin.skeu) 4.dp else if (Skin.nothing) 16.dp else 18.dp)) },
    actions: @Composable () -> Unit = {}, localPlaylistId: String? = null,
) {
    val tint = rememberArtColor(thumb, Accent2.copy(alpha = .5f))
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(if (Skin.skeu) listOf(tint.copy(alpha = .45f), Color.Transparent) else if (Skin.metro || Skin.nothing) listOf(Bg, Bg) else listOf(tint, Bg)))
                        .statusBarsPadding().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Artwork shrinks and fades as the list scrolls (parallax).
                    val off = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset else 10_000
                    val p = (with(density) { off.toDp().value } / 260f).coerceIn(0f, 1f)
                    art(
                        Modifier.size(240.dp).graphicsLayer { scaleX = 1f - p * .25f; scaleY = 1f - p * .25f; alpha = 1f - p; translationY = off * .5f }
                            .shadow(if (Skin.metro || Skin.nothing) 0.dp else 28.dp, corner(if (Skin.skeu) 4.dp else 18.dp)),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        title.mt(), fontSize = if (Skin.metro) 32.sp else if (Skin.nothing) 28.sp else 24.sp,
                        fontWeight = if (Skin.metro) FontWeight.Light else if (Skin.nothing) FontWeight.Medium else FontWeight.ExtraBold,
                        textAlign = TextAlign.Center, style = skeuText(), modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle, color = if (onSubtitle != null) (if (Skin.metro) MetroAccent else if (Skin.nothing) NdPrimary else Color.White.copy(alpha = .85f)) else Dim, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(8.dp))
                            .then(if (onSubtitle != null) Modifier.clickable(onClick = onSubtitle) else Modifier).padding(4.dp),
                    )
                    val min = tracks.sumOf { it.duration } / 60
                    val count = (if (tracks.size == 1) "1 brano" else "${tracks.size} brani") + if (min > 0) " • $min min" else ""
                    if (Skin.nothing) NdLabel(count, Modifier.padding(top = 4.dp)) else Text(count, color = Dim, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions()
                        DownloadAllButton(tracks)
                    }
                    Spacer(Modifier.height(6.dp))
                    PlayShuffleRow(tracks)
                }
            }
            itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
                TrackRow(
                    t, index = if (numbered) i + 1 else null,
                    subtitle = if (numbered) t.artist else listOfNotNull(t.artist.ifEmpty { null }, t.album).joinToString(" • "),
                    onClick = { Playback.play(tracks, i) }, onMore = { Ui.menu = MenuReq(t, localPlaylistId) },
                )
            }
        }
        BackButton(nav)
    }
}

/** Saves every song of an album/playlist on the phone; once all are saved, removes them. */
@Composable
fun DownloadAllButton(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    val all = Downloads.allSaved(tracks)
    val busy = tracks.any { Downloads.isActive(it.id) }
    IconButton(
        onClick = { if (busy) Downloads.cancel(tracks) else if (all) Downloads.remove(tracks) else Downloads.download(tracks) },
        modifier = Modifier.size(48.dp).roundButton(),
    ) {
        when {
            busy -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(30.dp), color = Accent, strokeWidth = 2.5.dp)
                Icon(Icons.Rounded.Close, "Annulla download", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            all -> Icon(Icons.Rounded.DownloadDone, "Rimuovi i download", tint = Accent)
            else -> Icon(Icons.Rounded.Download, "Scarica tutto", tint = Color.White)
        }
    }
}

@Composable
fun HeartButton(saved: Boolean, onClick: () -> Unit) {
    val scale by androidx.compose.animation.core.animateFloatAsState(if (saved) 1.15f else 1f, spring(dampingRatio = .35f, stiffness = 400f), label = "heart")
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).roundButton()) {
        Icon(
            if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (saved) "Rimuovi dalla libreria" else "Salva in libreria",
            tint = if (saved) (if (Skin.nothing) NdRed else Accent) else Color.White, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
fun AlbumScreen(id: String, nav: NavController) {
    val scope = rememberCoroutineScope()
    Loader(id, { Api.album(id) }) { p ->
        val a = p.album
        CollectionScreen(
            nav, a.title, listOfNotNull(a.artist.ifEmpty { null }, a.year).joinToString(" • "), a.thumb, p.tracks, numbered = true,
            onSubtitle = a.artistId?.let { aid -> { nav.navigate("artist/$aid") } },
            actions = { HeartButton(Lib.hasAlbum(a.id)) { Lib.scope.launch { Lib.toggleAlbum(a) } } },
        )
    }
}

@Composable
fun PlaylistScreen(id: String, nav: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Loader(id, { Api.playlist(id) }) { p ->
        CollectionScreen(
            nav, p.playlist.title, p.playlist.author, p.playlist.thumb, p.tracks, numbered = false,
            actions = {
                IconButton(
                    onClick = {
                        Lib.scope.launch {
                            Lib.importPlaylist(p.playlist.title, p.tracks)
                                .onSuccess { Toast.makeText(ctx, "Playlist salvata in libreria", Toast.LENGTH_SHORT).show() }
                        }
                    },
                    modifier = Modifier.size(48.dp).roundButton(),
                ) { Icon(Icons.Rounded.LibraryAdd, "Salva in libreria", tint = Color.White) }
            },
        )
    }
}

@Composable
fun LocalPlaylistScreen(id: String, nav: NavController) {
    val p = Lib.data.playlists.find { it.id == id }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    if (p == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    CollectionScreen(
        nav, p.name, "La tua playlist", p.tracks.firstOrNull()?.thumb, p.tracks, numbered = false, localPlaylistId = p.id,
        art = { Mosaic(p.tracks.mapNotNull { it.thumb }, it) },
        actions = {
            IconButton(onClick = { confirm = true }, modifier = Modifier.size(48.dp).roundButton()) {
                Icon(Icons.Rounded.Delete, "Elimina playlist", tint = Color.White)
            }
        },
    )
    if (p.tracks.isEmpty()) Box(Modifier.fillMaxSize().padding(top = 300.dp)) {
        EmptyState(Icons.Rounded.LibraryMusic, "Playlist vuota", "Dal menu ⋮ di un brano scegli \"Aggiungi a playlist\".")
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = CardHi,
        title = { Text("Eliminare \"${p.name}\"?") },
        text = { Text("La playlist verrà eliminata. I brani restano nella libreria.") },
        confirmButton = { TextButton(onClick = { confirm = false; Lib.scope.launch { Lib.deletePlaylist(p.id) } }) { Text("Elimina", color = Accent) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla", color = Dim) } },
    )
}

// ---------------- Artist ----------------

@Composable
fun ArtistScreen(id: String, nav: NavController) {
    val scope = rememberCoroutineScope()
    Loader(id, { Api.artist(id) }) { p ->
        val a = p.artist
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    // clipped: the parallax would push the photo down over the rows below
                    Box(Modifier.fillMaxWidth().height(340.dp).clipToBounds()) {
                        val off = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset else 0
                        Artwork(
                            a.thumb, Modifier.fillMaxSize().graphicsLayer { translationY = off * .45f; val s = 1f + off / 2400f; scaleX = s; scaleY = s },
                            RoundedCornerShape(0.dp), Icons.Rounded.Person,
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = .4f), Bg))))
                        Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                a.name.mt(), fontSize = if (Skin.metro) 46.sp else if (Skin.nothing) 44.sp else 36.sp,
                                fontWeight = if (Skin.metro) FontWeight.Light else if (Skin.nothing) FontWeight.Bold else FontWeight.ExtraBold,
                                lineHeight = if (Skin.metro) 48.sp else if (Skin.nothing) 46.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                                fontFamily = if (Skin.nothing) NdDot else null,
                                style = skeuText(), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            HeartButton(Lib.hasArtist(a.id)) { Lib.scope.launch { Lib.toggleArtist(a) } }
                        }
                    }
                }
                if (p.songs.isNotEmpty()) {
                    item {
                        PlayShuffleRow(p.songs) { Playback.shuffleAll(p.songs) { p.songsId?.let { Api.playlist(it).tracks }.orEmpty() } }
                    }
                    item { SectionTitle("Brani popolari") }
                    itemsIndexed(p.songs, key = { _, t -> "s-${t.id}" }) { i, t ->
                        TrackRow(t, onClick = { Playback.play(p.songs, i) }, onMore = { Ui.menu = MenuReq(t) })
                    }
                }
                listOf("Album" to p.albums, "Singoli ed EP" to p.singles).filter { it.second.isNotEmpty() }.forEach { (label, albums) ->
                    item { SectionTitle(label) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                            items(albums, key = { it.id }) { al ->
                                AlbumCard(al.title, al.year ?: "", al.thumb, size = 150.dp) { nav.navigate("album/${al.id}") }
                            }
                        }
                    }
                }
            }
            BackButton(nav)
        }
    }
}

@Composable
fun SectionTitle(t: String) = if (Skin.nothing) NdLabel(t, Modifier.padding(start = 20.dp, top = 36.dp, bottom = 10.dp)) else Text(
    t.mt(), fontSize = if (Skin.metro) 30.sp else 20.sp, fontWeight = if (Skin.metro) FontWeight.Light else FontWeight.Bold,
    style = skeuText(), modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
)

// ---------------- Profiles ----------------

private val profileGradients = listOf(
    listOf(Accent, Accent2), listOf(Color(0xFF22D3EE), Color(0xFF6366F1)), listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
    listOf(Color(0xFF10B981), Color(0xFF3B82F6)), listOf(Color(0xFFEC4899), Color(0xFFF97316)),
)

fun profileBrush(id: String): Brush = profileGradients[Math.floorMod(id.hashCode(), profileGradients.size)].let {
    if (Skin.metro) androidx.compose.ui.graphics.SolidColor(it[0]) // flat tiles in Modern UI
    else if (Skin.nothing) androidx.compose.ui.graphics.SolidColor(NdSurface) // monochrome in Nothing
    else Brush.linearGradient(it)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(onPick: (String) -> Unit) {
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(attempt) {
        failed = false
        runCatching { Api.users() }.onSuccess { profiles = it }.onFailure { failed = true }
    }
    fun act(block: suspend () -> List<Profile>) = Lib.scope.launch {
        runCatching { block() }.onSuccess { profiles = it }
            .onFailure { Toast.makeText(ctx, "Operazione non riuscita", Toast.LENGTH_SHORT).show() }
    }

    Column(
        Modifier.fillMaxSize().then(if (Skin.skeu || Skin.metro) Modifier else if (Skin.nothing) Modifier.dotGrid() else Modifier.background(Brush.verticalGradient(listOf(Accent2.copy(alpha = .30f), Bg, Bg)))).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        if (Skin.metro) Text("chi ascolta?", fontSize = 44.sp, fontWeight = FontWeight.Light)
        else if (Skin.nothing) NdHero("Chi ascolta?", size = 46.sp)
        else Text("Chi ascolta?", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, style = skeuText())
        Spacer(Modifier.height(6.dp))
        Text("Ogni profilo ha la sua libreria e le sue playlist", color = if (Skin.nothing) NdDisabled else Dim, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        val list = profiles
        when {
            failed -> {
                EmptyState(Icons.Rounded.WifiOff, "Server non raggiungibile", "Controlla che Tailscale sia attivo.")
                TextButton(onClick = { attempt++ }) { Text("Riprova", color = Accent) }
            }
            list == null -> Spinner()
            else -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(horizontal = 24.dp), modifier = Modifier.weight(1f)) {
                itemsIndexed(list, key = { _, p -> p.id }) { i, p ->
                    PopIn(i) {
                        ProfileBubble(
                            p.name, Modifier.combinedClickable(onLongClick = { deleting = p }, onClick = { onPick(p.id) }),
                        ) {
                            Box(Modifier.fillMaxSize().background(profileBrush(p.id)), contentAlignment = Alignment.Center) {
                                Text(
                                    p.name.take(1).uppercase(), fontSize = if (Skin.metro) 56.sp else if (Skin.nothing) 60.sp else 48.sp,
                                    fontWeight = if (Skin.metro) FontWeight.Light else FontWeight.ExtraBold, color = Color.White, fontFamily = if (Skin.nothing) NdDot else null,
                                )
                            }
                        }
                    }
                }
                item(key = "add") {
                    PopIn(list.size) {
                        ProfileBubble("Aggiungi", Modifier.clickable { creating = true }) {
                            Box(
                                Modifier.fillMaxSize().then(
                                    if (Skin.skeu) Modifier.background(Brush.radialGradient(listOf(Color(0xFF4A433C), Color(0xFF221E1A))))
                                    else if (Skin.metro) Modifier.border(2.dp, Color.White) else if (Skin.nothing) Modifier else Modifier.background(Card),
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = if (Skin.metro) Color.White else Dim, modifier = Modifier.size(44.dp))
                            }
                        }
                    }
                }
                if (list.isNotEmpty()) item(span = { GridItemSpan(2) }, key = "hint") {
                    if (Skin.nothing) Box(Modifier.padding(top = 24.dp), contentAlignment = Alignment.Center) { NdLabel("Tieni premuto un profilo per eliminarlo", color = NdDisabled, size = 10.sp) }
                    else Text(
                        "Tieni premuto un profilo per eliminarlo", color = Dim.copy(alpha = .7f), fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }

    if (creating) NewNameDialog("Nuovo profilo", "Crea", onDismiss = { creating = false }) { name ->
        creating = false
        act { Api.createUser(name) }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = CardHi,
            title = { Text("Eliminare il profilo \"${p.name}\"?") },
            text = { Text("La sua libreria e le sue playlist non saranno più visibili nell'app.") },
            confirmButton = { TextButton(onClick = { deleting = null; act { Api.deleteUser(p.id) } }) { Text("Elimina", color = Accent) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla", color = Dim) } },
        )
    }
}

/** Staggered scale+fade entrance for grid items. */
@Composable
private fun PopIn(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(70L * index); shown = true }
    val s by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else .6f, spring(dampingRatio = .55f, stiffness = 300f), label = "pop")
    val a by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(250), label = "popa")
    val metro = Skin.metro
    val fadeOnly = Skin.nothing // Nothing: no bounce, elements just fade in
    Box(Modifier.graphicsLayer { if (metro) { rotationX = (1f - a) * 90f; cameraDistance = 10 * density } else if (!fadeOnly) { scaleX = s; scaleY = s }; alpha = a }) { content() }
}

@Composable
private fun ProfileBubble(name: String, click: Modifier, avatar: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (Skin.skeu) {
            // glass dome in a metal bezel
            Box(
                Modifier.size(122.dp).shadow(18.dp, CircleShape).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF6A635C), Color(0xFF15120F)))).padding(4.dp)
                    .clip(CircleShape).then(click)
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .35f)), startY = size.height * .5f))
                        drawOval(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent), startY = 6f, endY = size.height * .5f),
                            topLeft = Offset(size.width * .14f, 6f), size = Size(size.width * .72f, size.height * .44f),
                        )
                    },
            ) { avatar() }
        } else if (Skin.metro) Box(Modifier.size(132.dp).then(click)) { avatar() } // square live tile
        else if (Skin.nothing) Box(Modifier.size(116.dp).clip(CircleShape).border(1.dp, NdBorderVisible, CircleShape).then(click)) { avatar() }
        else Box(Modifier.size(116.dp).shadow(18.dp, CircleShape).clip(CircleShape).then(click)) { avatar() }
        Spacer(Modifier.height(if (Skin.metro) 6.dp else 12.dp))
        if (Skin.nothing) NdLabel(name, color = NdPrimary, size = 12.sp)
        else Text(
            name.mt(), fontSize = if (Skin.metro) 20.sp else 17.sp, fontWeight = if (Skin.metro) FontWeight.Light else FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = skeuText(),
        )
    }
}
