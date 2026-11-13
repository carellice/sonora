// Data and state shared by every screen: API client, profile, library, player, navigation, overlays.
import { useSyncExternalStore } from 'react'

// ---------- tiny stores ----------
export function createStore(initial) {
  let state = initial
  const listeners = new Set()
  return {
    get: () => state,
    set(patch) {
      state = { ...state, ...(typeof patch === 'function' ? patch(state) : patch) }
      listeners.forEach((l) => l())
    },
    sub(l) { listeners.add(l); return () => listeners.delete(l) },
  }
}
export const useStore = (store) => useSyncExternalStore(store.sub, store.get)

// ---------- API ----------
const USER_KEY = 'sonora.user'
export const profile = {
  get id() { return localStorage.getItem(USER_KEY) || '' },
  set id(v) { if (v) localStorage.setItem(USER_KEY, v); else localStorage.removeItem(USER_KEY) },
}

async function req(method, path, body) {
  const headers = {}
  if (profile.id) headers['x-user'] = profile.id // the server keeps one library per profile
  if (body) headers['content-type'] = 'application/json'
  const r = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  if (!r.ok) { const e = new Error(`Errore server ${r.status}`); e.status = r.status; throw e }
  return r.json()
}
const enc = encodeURIComponent

export const api = {
  info: () => req('GET', '/api/info'),
  users: () => req('GET', '/api/users'),
  createUser: (name) => req('POST', '/api/users', { name }),
  deleteUser: (id) => req('DELETE', `/api/users/${enc(id)}`),
  search: (q, type) => req('GET', `/api/search?type=${type}&q=${enc(q)}`),
  album: (id) => req('GET', `/api/album/${enc(id)}`),
  artist: (id) => req('GET', `/api/artist/${enc(id)}`),
  playlist: (id) => req('GET', `/api/playlist/${enc(id)}`),
  radio: (id) => req('GET', `/api/radio/${enc(id)}`),
  prepare: (id) => fetch(`/api/prepare/${enc(id)}`).catch(() => {}),
  library: () => req('GET', '/api/library'),
  libAdd: (kind, item) => req('POST', `/api/library/${kind}`, item),
  libRemove: (kind, id) => req('DELETE', `/api/library/${kind}/${enc(id)}`),
  createPlaylist: (name) => req('POST', '/api/playlists', { name }),
  deletePlaylist: (id) => req('DELETE', `/api/playlists/${enc(id)}`),
  addToPlaylist: (pid, t) => req('POST', `/api/playlists/${enc(pid)}/tracks`, t),
  removeFromPlaylist: (pid, tid) => req('DELETE', `/api/playlists/${enc(pid)}/tracks/${enc(tid)}`),
}
export const streamUrl = (id) => `/api/stream/${enc(id)}`

// ---------- toast ----------
export const toastStore = createStore({ msg: null, key: 0 })
let toastTimer
export function toast(msg) {
  toastStore.set((s) => ({ msg, key: s.key + 1 }))
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => toastStore.set({ msg: null }), 2600)
}

// ---------- library (server side, per profile) ----------
const EMPTY = { songs: [], albums: [], artists: [], playlists: [] }
export const libStore = createStore({ data: EMPTY, loading: false })

async function run(promise) {
  try { libStore.set({ data: await promise }); return true } catch (e) {
    if (e.status === 401) leaveProfile()
    else toast('Operazione non riuscita')
    return false
  }
}
export const lib = {
  clear: () => libStore.set({ data: EMPTY }),
  async refresh() {
    libStore.set({ loading: true })
    const ok = await run(api.library())
    libStore.set({ loading: false })
    return ok
  },
  hasSong: (id) => libStore.get().data.songs.some((s) => s.id === id),
  hasAlbum: (id) => libStore.get().data.albums.some((s) => s.id === id),
  hasArtist: (id) => libStore.get().data.artists.some((s) => s.id === id),
  toggleSong: (t) => run(lib.hasSong(t.id) ? api.libRemove('songs', t.id) : api.libAdd('songs', t)),
  toggleAlbum: (a) => run(lib.hasAlbum(a.id) ? api.libRemove('albums', a.id) : api.libAdd('albums', a)),
  toggleArtist: (a) => run(lib.hasArtist(a.id) ? api.libRemove('artists', a.id) : api.libAdd('artists', a)),
  createPlaylist: (name) => run(api.createPlaylist(name)),
  deletePlaylist: (id) => run(api.deletePlaylist(id)),
  addToPlaylist: (pid, t) => run(api.addToPlaylist(pid, t)),
  removeFromPlaylist: (pid, tid) => run(api.removeFromPlaylist(pid, tid)),
  /** New library playlist; returns its id. */
  async newPlaylist(name) {
    const before = new Set(libStore.get().data.playlists.map((p) => p.id))
    if (!(await lib.createPlaylist(name))) return null
    return libStore.get().data.playlists.find((p) => !before.has(p.id))?.id ?? null
  },
  /** Copies a YouTube Music playlist into a new library playlist. */
  async importPlaylist(name, tracks) {
    const pid = await lib.newPlaylist(name)
    if (!pid) return false
    for (const t of tracks) await api.addToPlaylist(pid, t).then((d) => libStore.set({ data: d }))
    return true
  },
}

// ---------- profile session ----------
export const sessionStore = createStore({ user: profile.id })
export function enterProfile(id) {
  profile.id = id
  lib.clear()
  sessionStore.set({ user: id })
  lib.refresh()
}
export function leaveProfile() {
  profile.id = ''
  lib.clear()
  sessionStore.set({ user: '' })
}

// ---------- player ----------
const audio = new Audio()
audio.preload = 'auto'
export const playerStore = createStore({
  queue: [], order: [], pos: -1, current: null,
  playing: false, buffering: false, shuffle: false, repeat: 'off', volume: 1,
})
export const audioEl = audio

function shuffled(n, first) {
  const rest = [...Array(n).keys()].filter((i) => i !== first)
  for (let i = rest.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [rest[i], rest[j]] = [rest[j], rest[i]] }
  return [first, ...rest]
}

function load(pos, autoplay = true) {
  const s = playerStore.get()
  const t = s.queue[s.order[pos]]
  if (!t) return
  playerStore.set({ pos, current: t, buffering: true })
  audio.src = streamUrl(t.id)
  if (autoplay) audio.play().catch(() => {})
  mediaSession(t)
  prefetch()
}

function prefetch() {
  const s = playerStore.get()
  s.order.slice(s.pos + 1, s.pos + 3).forEach((i) => s.queue[i] && api.prepare(s.queue[i].id))
}

function mediaSession(t) {
  if (!('mediaSession' in navigator)) return
  navigator.mediaSession.metadata = new MediaMetadata({
    title: t.title, artist: t.artist, album: t.album || '',
    artwork: t.thumb ? [{ src: t.thumb, sizes: '544x544', type: 'image/jpeg' }] : [],
  })
}
if ('mediaSession' in navigator) {
  const ms = navigator.mediaSession
  ms.setActionHandler('play', () => audio.play())
  ms.setActionHandler('pause', () => audio.pause())
  ms.setActionHandler('nexttrack', () => player.next())
  ms.setActionHandler('previoustrack', () => player.previous())
  try { ms.setActionHandler('seekto', (d) => { audio.currentTime = d.seekTime }) } catch { /* old browsers */ }
}

audio.addEventListener('playing', () => playerStore.set({ playing: true, buffering: false }))
audio.addEventListener('pause', () => playerStore.set({ playing: false }))
audio.addEventListener('waiting', () => playerStore.set({ buffering: true }))
audio.addEventListener('canplay', () => playerStore.set({ buffering: false }))
audio.addEventListener('ended', () => {
  const s = playerStore.get()
  if (s.repeat === 'one') { audio.currentTime = 0; audio.play(); return }
  if (s.pos + 1 < s.order.length) load(s.pos + 1)
  else if (s.repeat === 'all' && s.order.length) load(0)
  else playerStore.set({ playing: false })
})
audio.addEventListener('error', () => {
  if (!audio.src) return
  toast('Impossibile riprodurre il brano')
  const s = playerStore.get()
  if (s.pos + 1 < s.order.length) load(s.pos + 1)
  else playerStore.set({ buffering: false, playing: false })
})

export const player = {
  play(list, index = 0, shuffle = false) {
    if (!list.length) return
    const start = shuffle ? Math.floor(Math.random() * list.length) : index
    const order = shuffle ? shuffled(list.length, start) : [...list.keys()]
    playerStore.set({ queue: list, order, shuffle })
    load(shuffle ? 0 : index)
  },
  /** Plays one song, then fills the queue with YouTube Music's radio for it. */
  async playRadio(t) {
    player.play([t])
    try {
      const r = (await api.radio(t.id)).filter((x) => x.id !== t.id)
      const s = playerStore.get()
      if (s.queue.length === 1 && s.current?.id === t.id) {
        playerStore.set({ queue: [t, ...r], order: [0, ...r.map((_, i) => i + 1)] })
        prefetch()
      }
    } catch { /* radio is a bonus */ }
  },
  playNext(t) {
    const s = playerStore.get()
    if (!s.current) return player.play([t])
    const queue = [...s.queue, t]
    const order = [...s.order]
    order.splice(s.pos + 1, 0, queue.length - 1)
    playerStore.set({ queue, order })
    prefetch()
  },
  enqueue(t) {
    const s = playerStore.get()
    if (!s.current) return player.play([t])
    playerStore.set({ queue: [...s.queue, t], order: [...s.order, s.queue.length] })
  },
  toggle() {
    if (!playerStore.get().current) return
    if (audio.paused) audio.play().catch(() => {}); else audio.pause()
  },
  next() {
    const s = playerStore.get()
    if (s.pos + 1 < s.order.length) load(s.pos + 1)
    else if (s.repeat === 'all' && s.order.length) load(0)
  },
  /** track = true (swipe): always the previous track, without restarting the song first. */
  previous(track) {
    const s = playerStore.get()
    if ((track !== true && audio.currentTime > 3) || s.pos === 0) audio.currentTime = 0
    else load(s.pos - 1)
  },
  seek: (sec) => { audio.currentTime = sec },
  setVolume: (v) => { audio.volume = v; playerStore.set({ volume: v }) },
  toggleShuffle() {
    const s = playerStore.get()
    if (!s.current) return playerStore.set({ shuffle: !s.shuffle })
    const cur = s.order[s.pos]
    const order = s.shuffle ? [...s.queue.keys()] : shuffled(s.queue.length, cur)
    playerStore.set({ shuffle: !s.shuffle, order, pos: order.indexOf(cur) })
    prefetch()
  },
  cycleRepeat() {
    const r = playerStore.get().repeat
    playerStore.set({ repeat: r === 'off' ? 'all' : r === 'all' ? 'one' : 'off' })
  },
  upNext() {
    const s = playerStore.get()
    return s.order.slice(s.pos + 1).map((i, k) => ({ pos: s.pos + 1 + k, t: s.queue[i] }))
  },
  jumpTo: (pos) => load(pos),
  stop() { audio.pause(); audio.removeAttribute('src'); playerStore.set({ queue: [], order: [], pos: -1, current: null, playing: false }) },
}

// ---------- navigation (backed by browser history, so Back works everywhere) ----------
export const navStore = createStore({ stack: [{ name: 'library' }] })
export const uiStore = createStore({ menu: null, addTo: null, player: false, queue: false, settings: false })

export function navigate(route) {
  navStore.set((s) => ({ stack: [...s.stack, route] }))
  uiStore.set({ player: false, menu: null })
  history.pushState({ depth: navStore.get().stack.length }, '')
}
export const goBack = () => history.back()
export function setTab(name) {
  navStore.set({ stack: [{ name }] })
  history.replaceState({ depth: 1 }, '')
}
export function openPlayer() {
  if (uiStore.get().player) return
  uiStore.set({ player: true })
  history.pushState({ depth: navStore.get().stack.length, player: true }, '')
}
export const closePlayer = () => { if (uiStore.get().player) history.back() }

history.replaceState({ depth: 1 }, '')
window.addEventListener('popstate', (e) => {
  const st = e.state || { depth: 1 }
  uiStore.set({ player: !!st.player, menu: null })
  navStore.set((s) => ({ stack: s.stack.slice(0, Math.max(1, st.depth)) }))
})

// ---------- artwork color (dark, saturated, like the Android Palette) ----------
const colors = new Map()
export function artColor(url) {
  if (!url) return Promise.resolve(null)
  if (colors.has(url)) return colors.get(url)
  const p = new Promise((resolve) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      try {
        const c = document.createElement('canvas')
        c.width = c.height = 24
        const g = c.getContext('2d', { willReadFrequently: true })
        g.drawImage(img, 0, 0, 24, 24)
        const d = g.getImageData(0, 0, 24, 24).data
        let r = 0, gr = 0, b = 0, w = 0
        for (let i = 0; i < d.length; i += 4) {
          const mx = Math.max(d[i], d[i + 1], d[i + 2]), mn = Math.min(d[i], d[i + 1], d[i + 2])
          const weight = (mx - mn) / 255 + 0.05 // favour saturated pixels, as Palette's vibrant swatch
          r += d[i] * weight; gr += d[i + 1] * weight; b += d[i + 2] * weight; w += weight
        }
        resolve(hslFix(r / w, gr / w, b / w))
      } catch { resolve(null) } // image without CORS headers
    }
    img.onerror = () => resolve(null)
    img.src = url
  })
  colors.set(url, p)
  return p
}
function hslFix(r, g, b) {
  r /= 255; g /= 255; b /= 255
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b)
  let h = 0, s = 0
  const l = (mx + mn) / 2
  if (mx !== mn) {
    const d = mx - mn
    s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn)
    h = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4
    h /= 6
  }
  const L = Math.min(0.34, Math.max(0.22, l))
  return `hsl(${Math.round(h * 360)} ${Math.round(Math.min(1, s * 1.2) * 100)}% ${Math.round(L * 100)}%)`
}

export const fmt = (sec) => (!sec || sec <= 0 ? '' : `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`)
export const has = (s, q) => (s || '').toLowerCase().includes(q.trim().toLowerCase())
