package it.casa.sonora

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Songs saved on the phone to listen without a connection, shared by every profile on this phone.
 * They go either to the app's private storage (filesDir/downloads, removed with the app) or to a folder
 * the user picked (Storage Access Framework, kept as "Artista - Titolo.m4a"). Each saved song remembers its
 * own location, so changing the setting only affects new downloads. The list is downloads.json.
 */
object Downloads {
    var saved by mutableStateOf<List<Track>>(emptyList())
        private set
    /** Track id -> progress 0..1 (0 while waiting its turn). */
    var active by mutableStateOf<Map<String, Float>>(emptyMap())
        private set
    /** Songs being downloaded or waiting, in order: shown with their progress in the "Scaricati" tab. */
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    private val jobs = mutableMapOf<String, Job>()
    private val conns = mutableMapOf<String, HttpURLConnection>() // closed on cancel: unblocks a read waiting for the server
    /** Folder picked by the user (tree URI), or null for the app's own storage. */
    var folder by mutableStateOf<Uri?>(null)
        private set

    lateinit var appDir: File
        private set
    private lateinit var app: Context
    private val uris = mutableMapOf<String, String>() // track id -> file:// or content:// of the saved song
    private val dir get() = File(appDir, "downloads").apply { mkdirs() }
    private val index get() = File(appDir, "downloads.json")
    private val prefs get() = app.getSharedPreferences("sonora", Context.MODE_PRIVATE)
    private val scope = MainScope()
    private val slots = Semaphore(2) // the home server resolves each song with yt-dlp: two at a time is plenty

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        appDir = app.filesDir
        folder = prefs.getString("download_folder", null)?.let(Uri::parse)
        val list = runCatching { JSONArray(index.readText()) }.getOrNull() ?: JSONArray()
        val tracks = mutableListOf<Track>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val uri = o.optString("uri").ifEmpty { Uri.fromFile(File(dir, "${o.getString("id")}.m4a")).toString() }
            if (exists(Uri.parse(uri))) { tracks += Track.from(o); uris[o.getString("id")] = uri }
        }
        saved = tracks
    }

    private fun exists(uri: Uri): Boolean =
        if (uri.scheme == "file") File(uri.path!!).exists()
        else runCatching { app.contentResolver.query(uri, null, null, null, null)?.use { it.count > 0 } == true }.getOrDefault(false)

    fun isSaved(id: String) = id in uris
    fun isActive(id: String) = id in active
    fun allSaved(tracks: List<Track>) = tracks.isNotEmpty() && tracks.all { isSaved(it.id) }

    /** The saved copy of a song, otherwise its stream on the server. */
    fun uriFor(t: Track): Uri = uris[t.id]?.let(Uri::parse) ?: Uri.parse(Api.streamUrl(t.id))

    fun folderName(): String? = folder?.let { DocumentsContract.getTreeDocumentId(it).substringAfter(':').ifEmpty { "Memoria interna" } }

    /** Called with the tree URI from ACTION_OPEN_DOCUMENT_TREE, or null to go back to the app's storage. */
    fun chooseFolder(uri: Uri?) {
        uri?.let { app.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        folder = uri
        prefs.edit { if (uri == null) remove("download_folder") else putString("download_folder", uri.toString()) }
    }

    fun sizeBytes(): Long = saved.sumOf { t ->
        val u = Uri.parse(uris[t.id] ?: return@sumOf 0L)
        if (u.scheme == "file") File(u.path!!).length()
        else runCatching { app.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } ?: 0L }.getOrDefault(0L)
    }

    private suspend fun saveIndex() = withContext(Dispatchers.IO) {
        val tmp = File(appDir, "downloads.json.tmp")
        tmp.writeText(JSONArray(saved.map { it.json().put("uri", uris[it.id]) }).toString())
        tmp.renameTo(index)
    }

    fun download(tracks: List<Track>) {
        val todo = tracks.distinctBy { it.id }.filter { !isSaved(it.id) && !isActive(it.id) }
        if (todo.isEmpty()) return
        active = active + todo.associate { it.id to 0f }
        queue = queue + todo
        for (t in todo) jobs[t.id] = scope.launch { slots.withPermit { fetch(t) } }
    }

    /** Stops downloads (in progress or waiting); a partial file is deleted. */
    fun cancel(tracks: List<Track>) {
        val ids = tracks.map { it.id }.filter { it in active }.toSet()
        if (ids.isEmpty()) return
        active = active - ids
        queue = queue.filter { it.id !in ids }
        for (id in ids) {
            jobs.remove(id)?.cancel()
            synchronized(conns) { conns.remove(id) }?.let { c -> scope.launch(Dispatchers.IO) { c.disconnect() } }
        }
    }

    fun cancelAll() = cancel(queue)

    /** Overall progress of the queue, 0..1. */
    fun queueProgress(): Float = if (queue.isEmpty()) 0f else queue.sumOf { (active[it.id] ?: 0f).toDouble() }.toFloat() / queue.size

    /** Where the next song goes: a new document in the picked folder, or a .part file in the app's storage. */
    private fun target(t: Track): Pair<Uri, () -> OutputStream> {
        val tree = folder
        if (tree == null) {
            val part = File(dir, "${t.id}.part")
            return Uri.fromFile(part) to { part.outputStream() }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = "${t.artist.substringBefore(", ")} - ${t.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        val doc = DocumentsContract.createDocument(app.contentResolver, parent, "audio/mp4", name)
            ?: error("cartella non scrivibile")
        return doc to { app.contentResolver.openOutputStream(doc)!! }
    }

    private suspend fun fetch(t: Track) {
        var where: Uri? = null
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                // The tagged file; a server from before it only has the plain stream.
                var c = open(t.id, Api.downloadUrl(t))
                if (c.responseCode == 404) { c.disconnect(); c = open(t.id, Api.streamUrl(t.id)) }
                try {
                    if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                    val total = c.contentLengthLong
                    val (uri, open) = target(t)
                    where = uri
                    var done = 0L
                    var shown = 0f
                    c.inputStream.use { input ->
                        open().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val p = if (total > 0) done.toFloat() / total else 0f
                                if (p - shown >= 0.02f) {
                                    shown = p
                                    withContext(Dispatchers.Main) { if (isActive(t.id)) active = active + (t.id to p) }
                                }
                            }
                        }
                    }
                    if (total > 0 && done != total) error("download incompleto")
                    if (uri.scheme == "file") {
                        val final = File(dir, "${t.id}.m4a")
                        check(File(uri.path!!).renameTo(final))
                        where = Uri.fromFile(final)
                    }
                } finally {
                    synchronized(conns) { conns.remove(t.id) }
                    c.disconnect()
                }
            }
        }.isSuccess
        val cancelled = !isActive(t.id) // cancel() already took it off the list
        active = active - t.id
        queue = queue.filter { it.id != t.id }
        jobs.remove(t.id)
        if (ok) {
            uris[t.id] = where.toString()
            saved = saved.filter { it.id != t.id } + t
            saveIndex()
        } else {
            where?.let { delete(it) }
            if (!cancelled) Toast.makeText(app, "Download non riuscito: ${t.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun open(id: String, url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        synchronized(conns) { conns[id] = c }
        c.connectTimeout = 30_000
        c.readTimeout = 120_000 // the first byte waits for the server to fetch and tag the whole song
        return c
    }

    private fun delete(uri: Uri) {
        if (uri.scheme == "file") File(uri.path!!).delete()
        else runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
    }

    fun remove(tracks: List<Track>) {
        for (t in tracks) uris.remove(t.id)?.let { delete(Uri.parse(it)) }
        saved = saved.filter { it.id in uris }
        scope.launch { saveIndex() }
    }
}
