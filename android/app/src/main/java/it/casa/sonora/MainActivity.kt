package it.casa.sonora

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val prefs = getSharedPreferences("sonora", MODE_PRIVATE)
        Api.base = prefs.getString("server", "") ?: ""
        Api.user = prefs.getString("user", "") ?: ""
        Skin.init(prefs)
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        Downloads.init(this)
        Playback.attach(this)
        setContent { SonoraTheme { Root(prefs) } }
    }

    override fun onDestroy() {
        Playback.detach()
        super.onDestroy()
    }
}

@Composable
fun Root(prefs: SharedPreferences) {
    var server by rememberSaveable { mutableStateOf(Api.base) }
    var user by rememberSaveable { mutableStateOf(Api.user) }
    fun setUser(id: String) {
        prefs.edit { putString("user", id) }
        Api.user = id
        Lib.clear()
        user = id
    }
    Box(Modifier.fillMaxSize().background(Bg).then(if (Skin.skeu) Modifier.leather() else Modifier)) {
        when {
            server.isBlank() -> SetupScreen(prefs.getString("server", "") ?: "") { url ->
                prefs.edit { putString("server", url) }
                Api.base = url
                server = url
            }
            user.isBlank() -> ProfileScreen(onPick = ::setUser)
            else -> key(user) {
                Main(
                    onChangeProfile = { setUser("") },
                    onChangeServer = {
                        Playback.controller?.clearMediaItems()
                        prefs.edit { remove("server") }
                        Api.base = ""
                        server = ""
                        setUser("")
                    },
                )
            }
        }
    }
}

@Composable
fun Main(onChangeProfile: () -> Unit, onChangeServer: () -> Unit) {
    val nav = rememberNavController()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(Downloads::chooseFolder) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Lib.refresh().onFailure {
            // profile deleted from another phone: back to the picker
            if (it.message?.contains("401") == true) onChangeProfile()
            else Toast.makeText(ctx, "Server non raggiungibile", Toast.LENGTH_LONG).show()
        }
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    var update by remember { mutableStateOf<ApkInfo?>(null) }
    LaunchedEffect(Unit) {
        val installed = PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
        update = runCatching { Api.latestApk() }.getOrNull()?.takeIf { it.versionCode > installed }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = if (Skin.skeu) Color.Transparent else Bg,
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        Playback.current != null,
                        enter = slideInVertically(spring(dampingRatio = .7f)) { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) { MiniPlayer { playerOpen = true } }
                    val skeu = Skin.skeu
                    if (Skin.metro) MetroAppBar(route, nav) { settings = true }
                    else if (Skin.nothing) NothingNavBar(
                        listOf("library" to "Libreria", "search" to "Cerca"),
                        isOn = { r -> route == r || (r == "library" && route !in setOf("library", "search") && nav.previousBackStackEntry?.destination?.route == "library") },
                    ) { r ->
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    else NavigationBar(
                        containerColor = if (skeu) Color.Transparent else Bg, tonalElevation = 0.dp,
                        modifier = if (skeu) Modifier.metal(RectangleShape, 12.dp) else Modifier,
                    ) {
                        listOf(Triple("library", "Libreria", Icons.Rounded.LibraryMusic), Triple("search", "Cerca", Icons.Rounded.Search))
                            .forEach { (r, label, icon) ->
                                val isTop = route == r
                                NavigationBarItem(
                                    selected = isTop || (r == "library" && route !in setOf("library", "search") && nav.previousBackStackEntry?.destination?.route == "library"),
                                    onClick = {
                                        nav.navigate(r) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(icon, label) },
                                    label = { Text(label, fontWeight = FontWeight.SemiBold, style = if (skeu) OnMetal else androidx.compose.ui.text.TextStyle.Default) },
                                    colors = if (skeu) NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFE08400), selectedTextColor = Color(0xFFB86400), indicatorColor = Color.Black.copy(alpha = .18f),
                                        unselectedIconColor = MetalDim, unselectedTextColor = MetalDim,
                                    ) else NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = Accent.copy(alpha = .85f),
                                        unselectedIconColor = Dim, unselectedTextColor = Dim,
                                    ),
                                )
                            }
                    }
                }
            },
        ) { pad ->
            NavHost(
                nav, "library", Modifier.padding(bottom = pad.calculateBottomPadding()), // screens handle the status bar themselves
                // Modern UI: the page turnstiles in by itself (Turnstile), the navigator only cross-fades quickly
                // Nothing: pages only fade, no movement
                enterTransition = { if (Skin.metro) fadeIn(tween(120)) else if (Skin.nothing) fadeIn(tween(250)) else slideInHorizontally(tween(300)) { it / 8 } + fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(if (Skin.metro) 120 else 200)) },
                popEnterTransition = { fadeIn(tween(if (Skin.metro) 120 else 300)) },
                popExitTransition = { if (Skin.metro) fadeOut(tween(120)) else if (Skin.nothing) fadeOut(tween(200)) else slideOutHorizontally(tween(250)) { it / 8 } + fadeOut(tween(250)) },
            ) {
                composable("library") { Turnstile { LibraryScreen(nav) { settings = true } } }
                composable("search") { Turnstile { SearchScreen(nav) } }
                composable("album/{id}") { Turnstile { AlbumScreen(it.arguments?.getString("id") ?: "", nav) } }
                composable("artist/{id}") { Turnstile { ArtistScreen(it.arguments?.getString("id") ?: "", nav) } }
                composable("playlist/{id}") { Turnstile { PlaylistScreen(it.arguments?.getString("id") ?: "", nav) } }
                composable("local/{id}") { Turnstile { LocalPlaylistScreen(it.arguments?.getString("id") ?: "", nav) } }
            }
        }

        AnimatedVisibility(
            playerOpen,
            enter = slideInVertically(spring(dampingRatio = .85f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(280)),
        ) { FullPlayer { playerOpen = false } }
        // composed only while open, so it registers after NavHost's handler and wins over it
        if (playerOpen) BackHandler { playerOpen = false }

        UpdateBanner(update, onDismiss = { update = null })
        TrackMenu(nav) { playerOpen = false }
        AddToPlaylistDialog()
    }

    if (settings) AlertDialog(
        onDismissRequest = { settings = false }, containerColor = CardHi,
        title = { Text("Impostazioni".mt()) },
        text = {
            Column {
                if (Skin.nothing) NdLabel("Stile grafica") else Text("Stile grafica", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                // a list, not tabs: four names don't fit side by side
                Skin.styles.zip(listOf("Moderno", "Skeuomorfismo", "Modern UI", "Nothing")).forEach { (s, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { Skin.set(s) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(Skin.style == s, onClick = { Skin.set(s) }, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Dim))
                        Text(label.mt(), fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Server: ${Api.base}", color = Dim, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                if (Skin.nothing) NdLabel("Download") else Text("Download", fontWeight = FontWeight.SemiBold)
                Text(Downloads.folderName()?.let { "Cartella: $it" } ?: "Memoria dell'app (si cancella disinstallando l'app)", color = Dim, fontSize = 13.sp)
                TextButton(onClick = { pickFolder.launch(null) }) { Text("Scegli una cartella del telefono".mt(), color = Accent, fontSize = 16.sp) }
                if (Downloads.folder != null) TextButton(onClick = { Downloads.chooseFolder(null) }) { Text("Usa la memoria dell'app".mt(), color = Accent, fontSize = 16.sp) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { settings = false; onChangeProfile() }) { Text("Cambia profilo".mt(), color = Accent, fontSize = 16.sp) }
                TextButton(onClick = { settings = false; onChangeServer() }) { Text("Cambia server".mt(), color = Accent, fontSize = 16.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { settings = false }) { Text("Chiudi".mt(), color = Dim) } },
    )
}

/** "New version" card: the APK comes from the user's own server. */
@Composable
fun UpdateBanner(info: ApkInfo?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AnimatedVisibility(
        info != null,
        enter = slideInVertically(spring(dampingRatio = .7f)) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Row(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().shadow(16.dp, corner(18.dp))
                .clip(corner(18.dp)).background(AccentBrush).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Download, null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("Sonora ${info?.version ?: ""} è disponibile", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${Api.base}/sonora.apk")))
                onDismiss()
            }) { Text("Aggiorna", color = Color.White, fontWeight = FontWeight.Bold) }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Chiudi", tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenu(nav: NavController, closePlayer: () -> Unit) {
    val req = Ui.menu ?: return
    val t = req.track
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun close() { Ui.menu = null }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    ModalBottomSheet(onDismissRequest = ::close, sheetState = state, containerColor = CardHi) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(t.thumb, Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        val saved = Lib.hasSong(t.id)
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Riproduci dopo") { Playback.playNext(t); toast("Riprodotto dopo il brano attuale"); close() }
        MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Aggiungi in coda") { Playback.enqueue(t); toast("Aggiunto in coda"); close() }
        MenuItem(Icons.Rounded.Radio, "Avvia radio del brano") { Playback.playRadio(t); close() }
        MenuItem(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (saved) "Rimuovi dalla libreria" else "Salva in libreria") {
            Lib.scope.launch { Lib.toggleSong(t).onFailure { toast("Operazione non riuscita") } }; close()
        }
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Aggiungi a playlist") { Ui.addToPlaylist = t; close() }
        when {
            Downloads.isActive(t.id) -> MenuItem(Icons.Rounded.Close, "Annulla download") { Downloads.cancel(listOf(t)); close() }
            Downloads.isSaved(t.id) -> MenuItem(Icons.Rounded.Delete, "Rimuovi download") { Downloads.remove(listOf(t)); close() }
            else -> MenuItem(Icons.Rounded.Download, "Scarica") { Downloads.download(listOf(t)); toast("Download avviato"); close() }
        }
        req.localPlaylistId?.let { pid ->
            MenuItem(Icons.Rounded.RemoveCircleOutline, "Rimuovi da questa playlist") { Lib.scope.launch { Lib.removeFromPlaylist(pid, t.id) }; close() }
        }
        t.albumId?.let { id -> MenuItem(Icons.Rounded.Album, "Vai all'album") { close(); closePlayer(); nav.navigate("album/$id") } }
        t.artistId?.let { id -> MenuItem(Icons.Rounded.Person, "Vai all'artista") { close(); closePlayer(); nav.navigate("artist/$id") } }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (Skin.metro) return@Row Text(text.lowercase(), fontSize = 21.sp, fontWeight = FontWeight.Light)
        Icon(icon, null, tint = if (Skin.skeu) Accent else if (Skin.nothing) NdSecondary else Color.White)
        Spacer(Modifier.width(18.dp))
        Text(text, fontSize = 15.sp, style = skeuText())
    }
}

@Composable
fun AddToPlaylistDialog() {
    val t = Ui.addToPlaylist ?: return
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NewPlaylistDialog(onDismiss = { creating = false }) { name ->
            creating = false
            Lib.scope.launch {
                val before = Lib.data.playlists.map { it.id }.toSet()
                Lib.createPlaylist(name)
                Lib.data.playlists.firstOrNull { it.id !in before }?.let { Lib.addToPlaylist(it.id, t) }
                Toast.makeText(ctx, "Aggiunto a \"$name\"", Toast.LENGTH_SHORT).show()
            }
            Ui.addToPlaylist = null
        }
        return
    }
    AlertDialog(
        onDismissRequest = { Ui.addToPlaylist = null }, containerColor = CardHi,
        title = { Text("Aggiungi a playlist".mt()) },
        text = {
            LazyColumn {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { creating = true }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Text("Nuova playlist", color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(Lib.data.playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            Ui.addToPlaylist = null
                            Lib.scope.launch {
                                Lib.addToPlaylist(p.id, t)
                                Toast.makeText(ctx, "Aggiunto a \"${p.name}\"", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mosaic(p.tracks.mapNotNull { it.thumb }, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Ui.addToPlaylist = null }) { Text("Chiudi", color = Dim) } },
    )
}

/** Modern UI application bar: round outlined buttons with lowercase labels, "•••" opens the settings. */
@Composable
private fun MetroAppBar(route: String?, nav: NavController, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MetroChrome).navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.weight(1f))
        listOf(Triple("library", "libreria", Icons.Rounded.LibraryMusic), Triple("search", "cerca", Icons.Rounded.Search)).forEach { (r, label, icon) ->
            val on = route == r || (r == "library" && route !in setOf("library", "search") && nav.previousBackStackEntry?.destination?.route == "library")
            Column(
                Modifier.width(84.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                    nav.navigate(r) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(44.dp).ring().background(if (on) Color.White else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, label, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 12.sp, color = if (on) Color.White else MetroSubtle)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "•••", fontSize = 18.sp, letterSpacing = 2.sp, color = Color.White,
            modifier = Modifier.clickable(onClick = onMore).padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}
