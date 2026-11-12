package it.casa.sonora

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Track(
    val id: String, val title: String, val artist: String, val artistId: String?,
    val album: String?, val albumId: String?, val duration: Int, val thumb: String?,
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("title", title).put("artist", artist)
        .put("artistId", artistId ?: JSONObject.NULL).put("album", album ?: JSONObject.NULL)
        .put("albumId", albumId ?: JSONObject.NULL).put("duration", duration).put("thumb", thumb ?: JSONObject.NULL)

    companion object {
        fun from(o: JSONObject) = Track(
            o.getString("id"), o.optString("title"), o.optString("artist"), o.str("artistId"),
            o.str("album"), o.str("albumId"), o.optInt("duration"), o.str("thumb"),
        )
    }
}

data class Album(val id: String, val title: String, val artist: String, val artistId: String?, val year: String?, val thumb: String?) {
    fun json(): JSONObject = JSONObject().put("id", id).put("title", title).put("artist", artist)
        .put("artistId", artistId ?: JSONObject.NULL).put("year", year ?: JSONObject.NULL).put("thumb", thumb ?: JSONObject.NULL)

    companion object {
        fun from(o: JSONObject) = Album(o.getString("id"), o.optString("title"), o.optString("artist"), o.str("artistId"), o.str("year"), o.str("thumb"))
    }
}

data class Artist(val id: String, val name: String, val thumb: String?) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("thumb", thumb ?: JSONObject.NULL)

    companion object {
        fun from(o: JSONObject) = Artist(o.getString("id"), o.optString("name"), o.str("thumb"))
    }
}

data class Playlist(val id: String, val title: String, val author: String, val thumb: String?) {
    companion object {
        fun from(o: JSONObject) = Playlist(o.getString("id"), o.optString("title"), o.optString("author"), o.str("thumb"))
    }
}

data class LocalPlaylist(val id: String, val name: String, val tracks: List<Track>)

data class Library(val songs: List<Track>, val albums: List<Album>, val artists: List<Artist>, val playlists: List<LocalPlaylist>) {
    companion object {
        val EMPTY = Library(emptyList(), emptyList(), emptyList(), emptyList())
        fun from(o: JSONObject) = Library(
            o.getJSONArray("songs").map(Track::from),
            o.getJSONArray("albums").map(Album::from),
            o.getJSONArray("artists").map(Artist::from),
            o.getJSONArray("playlists").map { LocalPlaylist(it.getString("id"), it.optString("name"), it.getJSONArray("tracks").map(Track::from)) },
        )
    }
}

data class Profile(val id: String, val name: String)

data class ApkInfo(val version: String, val versionCode: Long)

class AlbumPage(val album: Album, val tracks: List<Track>)
class ArtistPage(val artist: Artist, val songs: List<Track>, val albums: List<Album>, val singles: List<Album>)
class PlaylistPage(val playlist: Playlist, val tracks: List<Track>)

fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }
fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

object Api {
    var base = ""
    var user = "" // profile id, sent with every request: the server keeps one library per profile

    /** "casaos.x.ts.net:10008/" -> "https://casaos.x.ts.net:10008" */
    fun normalize(url: String): String {
        val u = url.trim().trimEnd('/')
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    }

    private suspend fun req(method: String, path: String, body: JSONObject? = null, root: String = base): String =
        withContext(Dispatchers.IO) {
            val c = URL(root + path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 10_000
                c.readTimeout = 60_000
                if (user.isNotEmpty()) c.setRequestProperty("x-user", user)
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("content-type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                if (code >= 400) throw IOException("Errore server $code")
                text
            } finally {
                c.disconnect()
            }
        }

    private suspend fun obj(path: String) = JSONObject(req("GET", path))
    private suspend fun arr(path: String) = JSONArray(req("GET", path))
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun streamUrl(id: String) = "$base/api/stream/$id"

    suspend fun health(root: String): Boolean = JSONObject(req("GET", "/api/health", root = root)).optBoolean("ok")

    suspend fun searchSongs(q: String) = arr("/api/search?type=song&q=${enc(q)}").map(Track::from)
    suspend fun searchAlbums(q: String) = arr("/api/search?type=album&q=${enc(q)}").map(Album::from)
    suspend fun searchArtists(q: String) = arr("/api/search?type=artist&q=${enc(q)}").map(Artist::from)
    suspend fun searchPlaylists(q: String) = arr("/api/search?type=playlist&q=${enc(q)}").map(Playlist::from)

    suspend fun album(id: String): AlbumPage {
        val o = obj("/api/album/${enc(id)}")
        return AlbumPage(Album.from(o), o.getJSONArray("tracks").map(Track::from))
    }

    suspend fun artist(id: String): ArtistPage {
        val o = obj("/api/artist/${enc(id)}")
        return ArtistPage(
            Artist.from(o), o.getJSONArray("songs").map(Track::from),
            o.getJSONArray("albums").map(Album::from), o.getJSONArray("singles").map(Album::from),
        )
    }

    suspend fun playlist(id: String): PlaylistPage {
        val o = obj("/api/playlist/${enc(id)}")
        return PlaylistPage(Playlist.from(o), o.getJSONArray("tracks").map(Track::from))
    }

    suspend fun prepare(id: String) { req("GET", "/api/prepare/${enc(id)}") }

    /** Latest APK the server can hand out (it downloads it from the GitHub releases), or null. */
    suspend fun latestApk(): ApkInfo? = JSONObject(req("GET", "/api/info")).optJSONObject("apk")
        ?.let { ApkInfo(it.optString("version"), it.optLong("versionCode")) }

    suspend fun radio(id: String) = arr("/api/radio/${enc(id)}").map(Track::from)

    private fun profiles(s: String) = JSONArray(s).map { Profile(it.getString("id"), it.optString("name")) }
    suspend fun users() = profiles(req("GET", "/api/users"))
    suspend fun createUser(name: String) = profiles(req("POST", "/api/users", JSONObject().put("name", name)))
    suspend fun deleteUser(id: String) = profiles(req("DELETE", "/api/users/${enc(id)}"))

    suspend fun library() = Library.from(obj("/api/library"))
    suspend fun libAdd(kind: String, item: JSONObject) = Library.from(JSONObject(req("POST", "/api/library/$kind", item)))
    suspend fun libRemove(kind: String, id: String) = Library.from(JSONObject(req("DELETE", "/api/library/$kind/${enc(id)}")))
    suspend fun createPlaylist(name: String) = Library.from(JSONObject(req("POST", "/api/playlists", JSONObject().put("name", name))))
    suspend fun deletePlaylist(id: String) = Library.from(JSONObject(req("DELETE", "/api/playlists/${enc(id)}")))
    suspend fun addToPlaylist(pid: String, t: Track) = Library.from(JSONObject(req("POST", "/api/playlists/${enc(pid)}/tracks", t.json())))
    suspend fun removeFromPlaylist(pid: String, tid: String) =
        Library.from(JSONObject(req("DELETE", "/api/playlists/${enc(pid)}/tracks/${enc(tid)}")))
}

/** Library kept on the server; every mutation returns the fresh library. */
object Lib {
    /** Outlives screens and sheets: closing a menu must not cancel the save it started. */
    val scope = kotlinx.coroutines.MainScope()

    var data by mutableStateOf(Library.EMPTY)
        private set
    var loading by mutableStateOf(false)
        private set

    private suspend fun run(block: suspend () -> Library) = runCatching { data = block() }

    fun clear() { data = Library.EMPTY }

    suspend fun refresh(): Result<Unit> {
        loading = true
        return run { Api.library() }.also { loading = false }
    }

    fun hasSong(id: String) = data.songs.any { it.id == id }
    fun hasAlbum(id: String) = data.albums.any { it.id == id }
    fun hasArtist(id: String) = data.artists.any { it.id == id }

    suspend fun toggleSong(t: Track) = run { if (hasSong(t.id)) Api.libRemove("songs", t.id) else Api.libAdd("songs", t.json()) }
    suspend fun toggleAlbum(a: Album) = run { if (hasAlbum(a.id)) Api.libRemove("albums", a.id) else Api.libAdd("albums", a.json()) }
    suspend fun toggleArtist(a: Artist) = run { if (hasArtist(a.id)) Api.libRemove("artists", a.id) else Api.libAdd("artists", a.json()) }
    suspend fun createPlaylist(name: String) = run { Api.createPlaylist(name) }
    suspend fun deletePlaylist(id: String) = run { Api.deletePlaylist(id) }
    suspend fun addToPlaylist(pid: String, t: Track) = run { Api.addToPlaylist(pid, t) }
    suspend fun removeFromPlaylist(pid: String, tid: String) = run { Api.removeFromPlaylist(pid, tid) }

    /** Copies a YouTube Music playlist into a new library playlist. */
    suspend fun importPlaylist(name: String, tracks: List<Track>) = run {
        val before = data.playlists.map { it.id }.toSet()
        var lib = Api.createPlaylist(name)
        val pid = lib.playlists.first { it.id !in before }.id
        for (t in tracks) lib = Api.addToPlaylist(pid, t)
        lib
    }
}
