// Sonora server: the web app (PWA), the JSON API used by the web and Android apps (YouTube Music
// search/browse, one library per profile, audio streamed through this server because googlevideo
// URLs are bound to the server's IP) and the Android APK, downloaded from the GitHub releases.
import http from 'node:http';
import { readFile, writeFile, rename, stat, rm } from 'node:fs/promises';
import { readFileSync, createReadStream, createWriteStream } from 'node:fs';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { randomUUID } from 'node:crypto';
import { Innertube } from 'youtubei.js';

const PORT = Number(process.env.PORT || 8080);
const VERSION = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')).version;
const PUBLIC = process.env.PUBLIC_DIR || fileURLToPath(new URL('./public/', import.meta.url));
const REPO = process.env.GITHUB_REPO || 'carellice/sonora';
const GH_TOKEN = process.env.GITHUB_TOKEN || ''; // only needed while the repository is private
const DATA = process.env.DATA_DIR || '/data';
const LEGACY_LIB = `${DATA}/library.json`; // single library from before profiles: becomes the first profile's
const USERS = `${DATA}/users.json`;

let ytP = null;
const yt = () => (ytP ??= Innertube.create({}).catch((e) => { ytP = null; throw e; }));

// ---------- mapping ----------
const big = (u) => u?.replace(/=w\d+-h\d+/, '=w544-h544').replace(/=s\d+$/, '=s544') ?? null;
const thumbOf = (i) => big(i?.thumbnail?.contents?.at?.(-1)?.url ?? i?.thumbnail?.at?.(-1)?.url ?? i?.thumbnails?.at?.(-1)?.url);
const items = (r) => (r?.contents ?? []).flatMap((x) => x.contents ?? []).filter((x) => x.type === 'MusicResponsiveListItem');

function track(i, fb = {}) {
  const id = i.id ?? i.video_id;
  if (!id) return null;
  const artists = i.artists?.length ? i.artists : i.authors?.length ? i.authors : i.author ? [i.author] : [];
  return {
    id,
    title: i.title?.toString?.() ?? i.title ?? 'Sconosciuto',
    artist: artists.map((a) => a.name).join(', ') || fb.artist || '',
    artistId: artists[0]?.channel_id || fb.artistId || null,
    album: i.album?.name ?? fb.album ?? null,
    albumId: i.album?.id ?? fb.albumId ?? null,
    duration: i.duration?.seconds ?? 0,
    thumb: thumbOf(i) ?? fb.thumb ?? null,
  };
}
const albumItem = (i) => ({
  id: i.id, title: i.title?.toString?.() ?? i.title ?? '',
  artist: i.author?.name ?? i.artists?.[0]?.name ?? '', artistId: i.author?.channel_id ?? i.artists?.[0]?.channel_id ?? null,
  year: i.year ?? null, thumb: thumbOf(i),
});
const artistItem = (i) => ({ id: i.id, name: i.name ?? i.title?.toString?.() ?? '', thumb: thumbOf(i) });
const playlistItem = (i) => ({ id: i.id, title: i.title?.toString?.() ?? i.title ?? '', author: i.author?.name ?? '', thumb: thumbOf(i) });

// ---------- YouTube Music ----------
async function search(q, type) {
  const r = await (await yt()).music.search(q, { type });
  const list = items(r).filter((i) => i.id);
  const map = { song: (i) => track(i), album: albumItem, artist: artistItem, playlist: playlistItem }[type];
  return list.map(map).filter(Boolean);
}

async function album(id) {
  const a = await (await yt()).music.getAlbum(id);
  const h = a.header ?? {};
  const info = {
    id, title: h.title?.toString() ?? '', artist: h.strapline_text_one?.toString() ?? '',
    artistId: h.strapline_text_one?.runs?.[0]?.endpoint?.payload?.browseId ?? null,
    year: h.subtitle?.toString()?.match(/\d{4}/)?.[0] ?? null, thumb: thumbOf(h),
  };
  const fb = { artist: info.artist, artistId: info.artistId, album: info.title, albumId: id, thumb: info.thumb };
  return { ...info, tracks: a.contents.map((i) => track(i, fb)).filter(Boolean) };
}

async function artist(id) {
  const a = await (await yt()).music.getArtist(id);
  const shelf = (re) => a.sections.find((s) => re.test(s.header?.title?.toString?.() ?? s.title?.toString?.() ?? ''));
  const top = a.sections.find((s) => s.type === 'MusicShelf');
  const name = a.header?.title?.toString() ?? '';
  const own = (x) => ({ ...x, artist: x.artist || name, artistId: x.artistId || id });
  return {
    id, name, thumb: thumbOf(a.header),
    songsId: top?.title?.endpoint?.payload?.browseId?.replace(/^VL/, '') ?? null, // playlist with all the artist's songs
    songs: (top?.contents ?? []).map((i) => track(i, { artist: name, artistId: id })).filter(Boolean),
    albums: (shelf(/^album/i)?.contents ?? []).map(albumItem).map(own),
    singles: (shelf(/single/i)?.contents ?? []).map(albumItem).map(own),
  };
}

async function playlist(id) {
  const p = await (await yt()).music.getPlaylist(id);
  const h = p.header ?? {};
  const all = [...(p.items ?? p.contents ?? [])];
  for (let c = p; c.has_continuation && all.length < 1000;) { c = await c.getContinuation(); all.push(...(c.items ?? c.contents ?? [])); }
  return {
    id, title: h.title?.toString() ?? '', author: (h.strapline_text_one?.toString() ?? '').replace(/^N\/A$/, ''), thumb: thumbOf(h),
    tracks: all.map((i) => track(i)).filter(Boolean),
  };
}

async function radio(videoId) {
  const u = await (await yt()).music.getUpNext(videoId, true);
  return (u.contents ?? []).map((i) => track(i)).filter((t) => t && t.id !== videoId);
}

// ---------- streaming ----------
// Two URLs for the same file (itag 140). youtubei.js's ANDROID_VR one comes in ~0.2s, but without a PO token
// googlevideo serves only its first ~1 MB (about a minute of audio). yt-dlp's serves the whole file but takes
// ~5s to resolve, plus ~2s before googlevideo accepts it. Playback starts on the first while the second resolves.
const FAST_LIMIT = 1 << 20;
const fasts = new Map(); // videoId -> Promise<{ url, total, mime } | null>
function fastUrl(id) {
  if (!fasts.has(id)) {
    fasts.set(id, yt().then((y) => y.getStreamingData(id, { type: 'audio', format: 'mp4', itag: 140, client: 'ANDROID_VR' })).then((f) => {
      const q = f.url ? new URL(f.url).searchParams : null;
      return q?.get('clen') ? { url: f.url, total: Number(q.get('clen')), mime: q.get('mime') } : null;
    }).catch(() => null)); // no fast URL: everything comes from yt-dlp's
    setTimeout(() => fasts.delete(id), 3600e3).unref(); // the URLs last ~6h
  }
  return fasts.get(id);
}

const streams = new Map(); // videoId -> { url, exp, issued } | Promise
function resolveUrl(id) {
  const c = streams.get(id);
  if (c && (c.then || c.exp > Date.now())) return c.then ? c : Promise.resolve(c);
  // visionos first: its URLs work at once and for the whole file, the tv client's often answer 403 for tens of seconds.
  const ytdlp = (extra) => new Promise((ok, ko) =>
    execFile('yt-dlp', [...extra, '-f', '140/bestaudio[ext=m4a]/bestaudio', '-g', '--no-playlist', `https://music.youtube.com/watch?v=${id}`],
      { timeout: 60000 }, (err, out) => { const url = out?.trim().split('\n')[0]; err || !url ? ko(err ?? new Error('no url')) : ok(url); }));
  const p = ytdlp(['--extractor-args', 'youtube:player_client=visionos']).catch(() => ytdlp([])).then(
    (url) => {
      const exp = Number(new URL(url).searchParams.get('expire')) * 1000 || Date.now() + 3 * 3600e3;
      const v = { url, exp: exp - 10 * 60e3, issued: Date.now() };
      streams.set(id, v); return v;
    },
    (err) => { streams.delete(id); throw err; });
  streams.set(id, p);
  return p;
}

/** GET on yt-dlp's URL. googlevideo answers 403 for a few seconds after the URL is issued, and once it has expired. */
async function fetchFull(id, headers, signal) {
  for (let refreshed = false; ;) {
    const { url, issued } = await resolveUrl(id);
    const r = await fetch(url, { headers, signal });
    if (r.status !== 403) return r;
    await r.body?.cancel();
    if (Date.now() - issued < 10e3) await new Promise((ok) => setTimeout(ok, 300));
    else if (!refreshed) { refreshed = true; streams.delete(id); }
    else return r;
  }
}

// googlevideo throttles long requests to about real-time speed; 1 MB ranges come at full speed (as yt-dlp does).
const CHUNK = 1 << 20;

/** Byte range asked by the client, clamped to the file: [start, end] inclusive, or null if unsatisfiable. */
function byteRange(header, total) {
  const m = /^bytes=(\d*)-(\d*)$/.exec(header || '');
  if (!m || (!m[1] && !m[2])) return [0, total - 1];
  const start = m[1] ? Number(m[1]) : Math.max(0, total - Number(m[2]));
  const end = m[1] && m[2] ? Math.min(Number(m[2]), total - 1) : total - 1;
  return start <= end ? [start, end] : null;
}

async function stream(id, req, res) {
  const ac = new AbortController();
  res.on('close', () => ac.abort());
  resolveUrl(id).catch(() => {}); // starts now, ready by the time the fast URL's part is played
  const fast = await fastUrl(id);
  let total = fast?.total, mime = fast?.mime;
  if (!fast) {
    const q = new URL((await resolveUrl(id)).url).searchParams;
    total = Number(q.get('clen')); mime = q.get('mime');
  }
  if (!total) { // no size in the URL: plain proxy
    const up = await fetchFull(id, req.headers.range ? { range: req.headers.range } : {}, ac.signal);
    const h = { 'accept-ranges': 'bytes', 'content-type': up.headers.get('content-type') ?? 'audio/mp4' };
    for (const k of ['content-length', 'content-range']) if (up.headers.get(k)) h[k] = up.headers.get(k);
    res.writeHead(up.status, h);
    if (!up.body || req.method === 'HEAD') return res.end();
    return Readable.fromWeb(up.body).on('error', () => res.destroy()).pipe(res);
  }
  const range = byteRange(req.headers.range, total);
  if (!range) { res.writeHead(416, { 'content-range': `bytes */${total}` }); return res.end(); }
  const [start, end] = range;
  const stop = (a) => Math.min(end, fast && a < FAST_LIMIT ? FAST_LIMIT - 1 : a + CHUNK - 1);
  const part = async (a) => {
    const headers = { range: `bytes=${a}-${stop(a)}` };
    if (fast && a < FAST_LIMIT) {
      const r = await fetch(fast.url, { headers, signal: ac.signal });
      if (r.ok) return r;
      await r.body?.cancel();
    }
    const r = await fetchFull(id, headers, ac.signal);
    if (fast && r.ok && Number(r.headers.get('content-range')?.split('/')[1]) !== total) throw new Error('yt-dlp picked another file');
    return r;
  };
  const first = await part(start);
  if (!first.ok) throw new Error(`googlevideo ${first.status}`);
  const h = { 'accept-ranges': 'bytes', 'content-type': mime || 'audio/mp4', 'content-length': end - start + 1 };
  if (req.headers.range) h['content-range'] = `bytes ${start}-${end}/${total}`;
  res.writeHead(req.headers.range ? 206 : 200, h);
  if (req.method === 'HEAD') { ac.abort(); return res.end(); }
  try {
    let up = first;
    for (let a = start; a <= end; a = stop(a) + 1) {
      if (a !== start) up = await part(a);
      if (!up.ok) throw new Error(`googlevideo ${up.status}`);
      for await (const chunk of up.body) {
        if (!res.write(chunk)) await new Promise((r) => res.once('drain', r));
      }
    }
    res.end();
  } catch { res.destroy(); } // client gone or upstream error: close, the player retries
}

// ---------- download ----------
// The whole song as a file for the phone, with title, artist, album and cover written in it: the stream alone has none.
// mutagen comes with yt-dlp[default]; if tagging fails the song is sent as it is.
const TAG = String.raw`import sys
from mutagen.mp4 import MP4, MP4Cover
f, title, artist, album, cover = sys.argv[1:6]
m = MP4(f)
m['\xa9nam'] = [title]
if artist: m['\xa9ART'] = [artist]; m['aART'] = [artist.split(', ')[0]]
if album: m['\xa9alb'] = [album]
if cover:
    data = open(cover, 'rb').read()
    m['covr'] = [MP4Cover(data, MP4Cover.FORMAT_PNG if data[:4] == b'\x89PNG' else MP4Cover.FORMAT_JPEG)]
m.save()`;
const COVER_HOSTS = /(^|\.)(googleusercontent\.com|ytimg\.com|ggpht\.com)$/;

async function download(id, q, res) {
  const tmp = join(DATA, `download-${randomUUID()}`);
  try {
    const r = await fetch(`http://127.0.0.1:${PORT}/api/stream/${id}`);
    if (!r.ok || !r.body) throw new Error(`stream ${r.status}`);
    await pipeline(Readable.fromWeb(r.body), createWriteStream(`${tmp}.m4a`));
    let cover = '';
    const thumb = q.get('thumb');
    if (thumb && /^https:$/.test(new URL(thumb).protocol) && COVER_HOSTS.test(new URL(thumb).hostname)) {
      const c = await fetch(thumb).catch(() => null);
      if (c?.ok) { cover = `${tmp}.cover`; await writeFile(cover, Buffer.from(await c.arrayBuffer())); }
    }
    const field = (k) => (q.get(k) ?? '').slice(0, 300);
    await new Promise((ok) => execFile('python3', ['-c', TAG, `${tmp}.m4a`, field('title') || id, field('artist'), field('album'), cover],
      { timeout: 30000 }, (err) => { if (err) console.error('tag', id, err.message); ok(); }));
    const { size } = await stat(`${tmp}.m4a`);
    res.writeHead(200, { 'content-type': 'audio/mp4', 'content-length': size });
    await pipeline(createReadStream(`${tmp}.m4a`), res);
  } finally {
    await rm(`${tmp}.m4a`, { force: true });
    await rm(`${tmp}.cover`, { force: true });
  }
}

// ---------- profiles & libraries ----------
async function saveJson(file, v) {
  await writeFile(file + '.tmp', JSON.stringify(v));
  await rename(file + '.tmp', file); // atomic: a crash mid-write never corrupts the file
}
// Only a missing file means "empty": any other read/parse error throws, so a later save can never
// overwrite real data with an empty library.
async function readJson(file, empty) {
  try { return JSON.parse(await readFile(file, 'utf8')); } catch (e) { if (e.code === 'ENOENT') return empty; throw e; }
}
let users = null;
async function loadUsers() {
  if (users) return users;
  users = await readJson(USERS, []);
  return users;
}
const libs = new Map();
const libFile = (uid) => `${DATA}/library-${uid}.json`;
async function loadLib(uid) {
  if (libs.has(uid)) return libs.get(uid);
  let l;
  l = await readJson(libFile(uid), {});
  for (const k of ['songs', 'albums', 'artists', 'playlists']) l[k] ??= [];
  libs.set(uid, l);
  return l;
}
const saveLib = (uid) => saveJson(libFile(uid), libs.get(uid));
function libraryView(l) {
  const albums = [...l.albums], artists = [...l.artists];
  for (const s of l.songs) {
    if (s.albumId && !albums.some((a) => a.id === s.albumId))
      albums.push({ id: s.albumId, title: s.album ?? '', artist: s.artist, artistId: s.artistId, year: null, thumb: s.thumb });
    if (s.artistId && !artists.some((a) => a.id === s.artistId))
      artists.push({ id: s.artistId, name: s.artist.split(', ')[0], thumb: null });
  }
  return { songs: l.songs, albums, artists, playlists: l.playlists };
}

// ---------- Android APK (latest GitHub release, kept in the data folder) ----------
const APK = () => `${DATA}/sonora.apk`;
const APK_INFO = () => `${DATA}/sonora-apk.json`;
const versionCode = (v) => { const [a = 0, b = 0, c = 0] = String(v).split('.').map(Number); return a * 10000 + b * 100 + c; };
let apkInfo = null;
async function loadApkInfo() {
  try { apkInfo = JSON.parse(await readFile(APK_INFO(), 'utf8')); await stat(APK()); } catch { apkInfo = null; }
}
async function syncApk() {
  try {
    const headers = { accept: 'application/vnd.github+json', 'user-agent': 'sonora-server' };
    if (GH_TOKEN) headers.authorization = `Bearer ${GH_TOKEN}`;
    const r = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers });
    if (!r.ok) return console.log(`apk: releases ${r.status}`);
    const rel = await r.json();
    const asset = rel.assets?.find((x) => x.name.endsWith('.apk'));
    const version = String(rel.tag_name || '').replace(/^v/, '');
    if (!asset || !version || apkInfo?.version === version) return;
    // the asset API URL redirects to signed storage; fetch drops the token on that cross-origin hop
    const d = await fetch(asset.url, { headers: { ...headers, accept: 'application/octet-stream' } });
    if (!d.ok) return console.log(`apk: download ${d.status}`);
    const buf = Buffer.from(await d.arrayBuffer());
    await writeFile(APK() + '.tmp', buf);
    await rename(APK() + '.tmp', APK());
    apkInfo = { version, versionCode: versionCode(version), size: buf.length, date: rel.published_at };
    await saveJson(APK_INFO(), apkInfo);
    console.log(`apk: ${version} scaricato`);
  } catch (e) { console.log('apk:', e.message); }
}

// ---------- web app (static files, SPA fallback) ----------
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
  '.woff2': 'font/woff2', '.txt': 'text/plain',
};
async function serveFile(res, file, headers = {}) {
  const st = await stat(file);
  res.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream', 'content-length': st.size, ...headers });
  createReadStream(file).pipe(res);
}
async function staticFile(req, res, pathname) {
  if (pathname === '/sonora.apk') {
    if (!apkInfo) return json(res, 404, { error: 'APK non ancora disponibile' });
    return serveFile(res, APK(), {
      'content-type': 'application/vnd.android.package-archive',
      'content-disposition': `attachment; filename="Sonora-${apkInfo.version}.apk"`, 'cache-control': 'no-cache',
    });
  }
  const file = normalize(join(PUBLIC, decodeURIComponent(pathname)));
  if (file.startsWith(normalize(PUBLIC)) && !pathname.endsWith('/')) {
    try {
      const immutable = pathname.startsWith('/assets/');
      return await serveFile(res, file, { 'cache-control': immutable ? 'public, max-age=31536000, immutable' : 'no-cache' });
    } catch { /* not a file: fall back to the app shell */ }
  }
  return serveFile(res, join(PUBLIC, 'index.html'), { 'cache-control': 'no-cache' });
}

// ---------- http ----------
const body = (req) => new Promise((ok, ko) => {
  let d = ''; req.on('data', (c) => { d += c; if (d.length > 1e6) req.destroy(); });
  req.on('end', () => { try { ok(d ? JSON.parse(d) : {}); } catch (e) { ko(e); } }); req.on('error', ko);
});
const json = (res, code, v) => { res.writeHead(code, { 'content-type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(v)); };
const ID = /^[\w-]{1,80}$/;

async function route(req, res) {
  const u = new URL(req.url, 'http://x');
  const p = u.pathname.split('/').filter(Boolean).map(decodeURIComponent); // ['api', ...]
  const m = req.method;
  if (p[0] !== 'api') return (m === 'GET' || m === 'HEAD') ? staticFile(req, res, u.pathname) : json(res, 404, { error: 'not found' });
  const [, a, b, c, d] = p;
  if (b && !ID.test(b)) return json(res, 400, { error: 'bad id' });

  if (a === 'health') return json(res, 200, { ok: true, name: 'sonora', version: VERSION });
  if (a === 'info') return json(res, 200, { name: 'Sonora', version: VERSION, apk: apkInfo });
  if (a === 'search' && m === 'GET') {
    const q = u.searchParams.get('q')?.trim(), type = u.searchParams.get('type') ?? 'song';
    if (!q || !['song', 'album', 'artist', 'playlist'].includes(type)) return json(res, 400, { error: 'q/type' });
    return json(res, 200, await search(q, type));
  }
  if (a === 'album' && b) return json(res, 200, await album(b));
  if (a === 'artist' && b) return json(res, 200, await artist(b));
  if (a === 'playlist' && b) return json(res, 200, await playlist(b));
  if (a === 'radio' && b) return json(res, 200, await radio(b));
  if (a === 'stream' && b) return stream(b, req, res);
  if (a === 'download' && b && m === 'GET') return download(b, u.searchParams, res);
  if (a === 'prepare' && b) { fastUrl(b); resolveUrl(b).catch(() => {}); return json(res, 202, { ok: true }); } // warm the URL cache for upcoming tracks

  if (a === 'users') {
    const us = await loadUsers();
    if (m === 'GET') return json(res, 200, us);
    if (m === 'POST' && !b) {
      const { name } = await body(req);
      if (typeof name !== 'string' || !name.trim()) return json(res, 400, { error: 'name' });
      const user = { id: randomUUID(), name: name.trim().slice(0, 40) };
      if (us.length === 0) await rename(LEGACY_LIB, libFile(user.id)).catch(() => {});
      us.push(user); await saveJson(USERS, us);
      return json(res, 200, us);
    }
    if (m === 'DELETE' && b) {
      users = us.filter((x) => x.id !== b); await saveJson(USERS, users);
      libs.delete(b);
      await rename(libFile(b), libFile(b) + '.deleted').catch(() => {}); // kept on disk, recoverable by hand
      return json(res, 200, users);
    }
  }
  if (a !== 'library' && a !== 'playlists') return json(res, 404, { error: 'not found' });
  const uid = req.headers['x-user'];
  if (typeof uid !== 'string' || !(await loadUsers()).some((x) => x.id === uid)) return json(res, 401, { error: 'profilo' });
  const l = await loadLib(uid);
  if (a === 'library') {
    if (m === 'GET' && !b) return json(res, 200, libraryView(l));
    if (!['songs', 'albums', 'artists'].includes(b)) return json(res, 400, { error: 'kind' });
    if (m === 'POST') {
      const it = await body(req);
      if (!it || typeof it.id !== 'string' || !ID.test(it.id)) return json(res, 400, { error: 'item.id' });
      l[b] = [it, ...l[b].filter((x) => x.id !== it.id)];
      await saveLib(uid); return json(res, 200, libraryView(l));
    }
    if (m === 'DELETE' && c) {
      l[b] = l[b].filter((x) => x.id !== c);
      if (b === 'albums') l.songs = l.songs.filter((s) => s.albumId !== c);
      if (b === 'artists') l.songs = l.songs.filter((s) => s.artistId !== c);
      await saveLib(uid); return json(res, 200, libraryView(l));
    }
  }
  if (a === 'playlists') {
    const pl = b && l.playlists.find((x) => x.id === b);
    if (m === 'POST' && !b) {
      const { name } = await body(req);
      if (typeof name !== 'string' || !name.trim()) return json(res, 400, { error: 'name' });
      l.playlists.push({ id: randomUUID(), name: name.trim().slice(0, 100), tracks: [] });
      await saveLib(uid); return json(res, 200, libraryView(l));
    }
    if (b && !pl) return json(res, 404, { error: 'playlist' });
    if (m === 'DELETE' && b && !c) { l.playlists = l.playlists.filter((x) => x !== pl); await saveLib(uid); return json(res, 200, libraryView(l)); }
    if (m === 'PATCH' && b && !c) {
      const { name } = await body(req);
      if (typeof name === 'string' && name.trim()) pl.name = name.trim().slice(0, 100);
      await saveLib(uid); return json(res, 200, libraryView(l));
    }
    if (m === 'POST' && c === 'tracks') {
      const t = await body(req);
      if (!t || typeof t.id !== 'string' || !ID.test(t.id)) return json(res, 400, { error: 'track.id' });
      pl.tracks = [...pl.tracks.filter((x) => x.id !== t.id), t];
      await saveLib(uid); return json(res, 200, libraryView(l));
    }
    if (m === 'DELETE' && c === 'tracks' && d) {
      pl.tracks = pl.tracks.filter((x) => x.id !== d);
      await saveLib(uid); return json(res, 200, libraryView(l));
    }
  }
  return json(res, 404, { error: 'not found' });
}

http.createServer((req, res) => {
  route(req, res).catch((e) => {
    console.error(req.method, req.url, e?.message);
    if (!res.headersSent) json(res, 502, { error: String(e?.message ?? e) }); else res.destroy();
  });
}).listen(PORT, '0.0.0.0', () => console.log(`Sonora ${VERSION} su :${PORT}`));

await loadApkInfo();
setTimeout(syncApk, 5000);
setInterval(syncApk, 6 * 3600e3);
