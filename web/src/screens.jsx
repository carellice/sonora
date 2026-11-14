import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlbumCard, ArtistRow, Artwork, Dialog, EmptyState, HeartButton, Icon, IconButton, Mosaic,
  NameDialog, PlayShuffle, SearchField, SegmentedTabs, Spinner, TrackRow, useArtColor, useLibrary,
} from './components.jsx'
import { api, enterProfile, goBack, has, lib, libStore, navigate, player, toast, useStore } from './core.js'

// ---------------- Profiles ----------------

const GRADIENTS = [
  ['#FF4D6D', '#8B5CF6'], ['#22D3EE', '#6366F1'], ['#F59E0B', '#EF4444'], ['#10B981', '#3B82F6'], ['#EC4899', '#F97316'],
]
// same hash as Kotlin's String.hashCode, so a profile has the same colour on the web and on Android
function hashCode(s) { let h = 0; for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0; return h }
export const profileGradient = (id) => {
  const [a, b] = GRADIENTS[((hashCode(id) % GRADIENTS.length) + GRADIENTS.length) % GRADIENTS.length]
  if (document.documentElement.classList.contains('metro')) return a // flat tiles in Modern UI
  return `linear-gradient(135deg, ${a}, ${b})`
}

export function ProfileScreen() {
  const [list, setList] = useState(null)
  const [failed, setFailed] = useState(false)
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const timer = useRef()
  const load = () => { setFailed(false); api.users().then(setList, () => setFailed(true)) }
  useEffect(load, [])
  const act = (p) => p.then(setList, () => toast('Operazione non riuscita'))
  const press = (p) => { timer.current = setTimeout(() => { timer.current = null; setDeleting(p) }, 600) }
  const release = (p) => { if (timer.current) { clearTimeout(timer.current); timer.current = null; enterProfile(p.id) } }

  return (
    <div className="profiles">
      <motion.h1 initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>Chi ascolta?</motion.h1>
      <p className="dim">Ogni profilo ha la sua libreria e le sue playlist</p>
      {failed ? (
        <EmptyState icon="wifi_off" title="Server non raggiungibile" text="Controlla la connessione e riprova.">
          <button className="text-btn" onClick={load}>Riprova</button>
        </EmptyState>
      ) : !list ? <div style={{ marginTop: 40 }}><Spinner /></div> : (
        <div className="profile-grid">
          {list.map((p, i) => (
            <motion.button key={p.id} className="profile" initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }}
              transition={{ type: 'spring', stiffness: 300, damping: 16, delay: i * 0.07 }} whileHover={{ scale: 1.06 }} whileTap={{ scale: 0.94 }}
              onPointerDown={() => press(p)} onPointerUp={() => release(p)} onPointerLeave={() => clearTimeout(timer.current)}
              onContextMenu={(e) => { e.preventDefault(); setDeleting(p) }}>
              <span className="profile-avatar" style={{ background: profileGradient(p.id) }}>{p.name.slice(0, 1).toUpperCase()}</span>
              <span className="profile-name">{p.name}</span>
            </motion.button>
          ))}
          <motion.button className="profile" initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }}
            transition={{ type: 'spring', stiffness: 300, damping: 16, delay: list.length * 0.07 }} whileHover={{ scale: 1.06 }} whileTap={{ scale: 0.94 }}
            onClick={() => setCreating(true)}>
            <span className="profile-avatar add"><Icon name="add" size={44} /></span>
            <span className="profile-name">Aggiungi</span>
          </motion.button>
        </div>
      )}
      {list?.length > 0 && <p className="hint">Tieni premuto un profilo per eliminarlo</p>}
      <NameDialog open={creating} title="Nuovo profilo" onClose={() => setCreating(false)}
        onDone={(name) => { setCreating(false); act(api.createUser(name)) }} />
      <Dialog open={!!deleting} onClose={() => setDeleting(null)} title={`Eliminare il profilo "${deleting?.name}"?`}
        actions={<><button className="text-btn dim" onClick={() => setDeleting(null)}>Annulla</button>
          <button className="text-btn" onClick={() => { act(api.deleteUser(deleting.id)); setDeleting(null) }}>Elimina</button></>}>
        La sua libreria e le sue playlist non saranno più visibili nell'app.
      </Dialog>
    </div>
  )
}

// ---------------- Library ----------------

const LIB_TABS = ['Brani', 'Album', 'Artisti', 'Playlist']

export function LibraryScreen({ onSettings }) {
  const data = useLibrary()
  const { loading } = useStore(libStore)
  const [tab, setTab] = useState(() => Number(sessionStorage.getItem('libTab') || 0))
  const [filter, setFilter] = useState('')
  const [creating, setCreating] = useState(false)
  const [dir, setDir] = useState(1)
  const select = (i) => { setDir(i > tab ? 1 : -1); setTab(i); setFilter(''); sessionStorage.setItem('libTab', i) }

  const songs = data.songs.filter((t) => !filter || has(t.title, filter) || has(t.artist, filter) || has(t.album, filter))
  const albums = data.albums.filter((a) => !filter || has(a.title, filter) || has(a.artist, filter))
  const artists = data.artists.filter((a) => !filter || has(a.name, filter))
  const pls = data.playlists.filter((p) => !filter || has(p.name, filter))

  return (
    <div className="screen">
      <div className="head">
        <h1 className="pivot-title">Libreria</h1>
        {loading && <Spinner size={20} />}
        {onSettings && <IconButton name="settings" label="Impostazioni" className="dim mobile-only" onClick={onSettings} />}
      </div>
      <div className="pad"><SegmentedTabs id="lib" tabs={LIB_TABS} selected={tab} onSelect={select} /></div>
      <div className="pad"><SearchField value={filter} onChange={setFilter} placeholder={`Cerca in ${LIB_TABS[tab].toLowerCase()}`} /></div>
      <AnimatePresence mode="wait" custom={dir} initial={false}>
        <motion.div key={tab} custom={dir} initial={{ opacity: 0, x: 40 * dir }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -40 * dir }}
          transition={{ duration: 0.22 }}>
          {tab === 0 && (data.songs.length === 0
            ? <EmptyState icon="music_note" title="Nessun brano salvato" text="Cerca un brano e tocca il cuore per aggiungerlo alla libreria." />
            : <><PlayShuffle tracks={songs} />{songs.map((t, i) => <TrackRow key={t.id} t={t} onClick={() => player.play(songs, i)} />)}</>)}
          {tab === 1 && (data.albums.length === 0
            ? <EmptyState icon="album" title="Nessun album" text="Gli album dei brani salvati e quelli che segui compaiono qui." />
            : <div className="grid">{albums.map((a) => <AlbumCard key={a.id} title={a.title} subtitle={a.artist} thumb={a.thumb} onClick={() => navigate({ name: 'album', id: a.id })} />)}</div>)}
          {tab === 2 && (data.artists.length === 0
            ? <EmptyState icon="person" title="Nessun artista" text="Gli artisti dei brani salvati e quelli che segui compaiono qui." />
            : artists.map((a) => <ArtistRow key={a.id} a={a} onClick={() => navigate({ name: 'artist', id: a.id })} />))}
          {tab === 3 && (
            <>
              <motion.div className="track" whileTap={{ scale: 0.97 }} onClick={() => setCreating(true)}>
                <div className="new-pl"><Icon name="add" size={30} /></div>
                <div className="track-text"><div className="track-title" style={{ fontSize: 16 }}>Nuova playlist</div></div>
              </motion.div>
              {pls.map((p) => (
                <motion.div key={p.id} className="track" whileTap={{ scale: 0.97 }} onClick={() => navigate({ name: 'local', id: p.id })}>
                  <Mosaic thumbs={p.tracks.map((t) => t.thumb).filter(Boolean)} size={56} />
                  <div className="track-text">
                    <div className="track-title" style={{ fontSize: 16 }}>{p.name}</div>
                    <div className="track-sub">{p.tracks.length === 1 ? '1 brano' : `${p.tracks.length} brani`}</div>
                  </div>
                </motion.div>
              ))}
            </>
          )}
        </motion.div>
      </AnimatePresence>
      <NameDialog open={creating} title="Nuova playlist" onClose={() => setCreating(false)} onDone={(n) => { setCreating(false); lib.createPlaylist(n) }} />
    </div>
  )
}

// ---------------- Search ----------------

const TYPES = [['Brani', 'song'], ['Album', 'album'], ['Artisti', 'artist'], ['Playlist', 'playlist']]
const searchState = { q: '', type: 0 } // kept while navigating to a result and back

export function SearchScreen() {
  const [q, setQ] = useState(searchState.q)
  const [type, setType] = useState(searchState.type)
  const [res, setRes] = useState({ key: '', list: [], loading: false, error: false })
  useEffect(() => { searchState.q = q; searchState.type = type }, [q, type])
  useEffect(() => {
    const query = q.trim()
    if (!query) { setRes({ key: '', list: [], loading: false, error: false }); return }
    const key = `${type}:${query}`
    setRes((r) => ({ ...r, loading: true, error: false }))
    const h = setTimeout(() => {
      api.search(query, TYPES[type][1]).then(
        (list) => setRes((r) => (searchState.q.trim() === query ? { key, list, loading: false, error: false } : r)),
        () => setRes({ key, list: [], loading: false, error: true }),
      )
    }, 350) // debounce typing
    return () => clearTimeout(h)
  }, [q, type])

  const kind = TYPES[type][1]
  return (
    <div className="screen">
      <div className="head"><h1>Cerca</h1></div>
      <div className="pad"><SearchField big value={q} onChange={setQ} placeholder="Artisti, brani, album…" autoFocus={!q} /></div>
      <div className="pad"><SegmentedTabs id="search" tabs={TYPES.map((t) => t[0])} selected={type} onSelect={setType} /></div>
      <div className="progress">{res.loading && <div className="progress-bar" />}</div>
      {!q.trim() ? <EmptyState icon="search" title="Trova la tua musica" text="Cerca tra milioni di brani, album, artisti e playlist di YouTube Music." />
        : res.error ? <EmptyState icon="wifi_off" title="Ricerca non riuscita" text="Controlla la connessione al server e riprova." />
          : !res.loading && res.list.length === 0 && res.key ? <EmptyState icon="search" title="Nessun risultato" text="Prova con parole diverse." />
            : (
              <motion.div key={res.key} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.2 }}>
                {res.key.startsWith(`${type}:`) && (kind === 'song' ? res.list.map((t) => <TrackRow key={t.id} t={t} onClick={() => player.playRadio(t)} />)
                  : kind === 'artist' ? res.list.map((a) => <ArtistRow key={a.id} a={a} onClick={() => navigate({ name: 'artist', id: a.id })} />)
                    : (
                      <div className="grid">
                        {res.list.map((a) => kind === 'album'
                          ? <AlbumCard key={a.id} title={a.title} subtitle={[a.artist, a.year].filter(Boolean).join(' • ')} thumb={a.thumb} onClick={() => navigate({ name: 'album', id: a.id })} />
                          : <AlbumCard key={a.id} title={a.title} subtitle={a.author} thumb={a.thumb} onClick={() => navigate({ name: 'playlist', id: a.id })} />)}
                      </div>
                    ))}
              </motion.div>
            )}
    </div>
  )
}

// ---------------- Detail pages ----------------

function useLoad(fn, key) {
  const [state, set] = useState({ data: null, failed: false })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let alive = true
    set({ data: null, failed: false })
    fn().then((data) => alive && set({ data, failed: false }), () => alive && set({ data: null, failed: true }))
    return () => { alive = false }
  }, [key, attempt]) // eslint-disable-line react-hooks/exhaustive-deps
  return { ...state, retry: () => setAttempt((a) => a + 1) }
}

function Loading({ state, children }) {
  if (state.data) return children(state.data)
  return (
    <div className="screen center-fill">
      <BackButton />
      {state.failed ? (
        <EmptyState icon="wifi_off" title="Caricamento non riuscito" text="Controlla la connessione al server.">
          <button className="text-btn" onClick={state.retry}>Riprova</button>
        </EmptyState>
      ) : <Spinner />}
    </div>
  )
}

export const BackButton = () => <IconButton name="arrow_back" label="Indietro" className="back-btn" onClick={goBack} />

function useScroll() {
  const [y, setY] = useState(0)
  return [y, (e) => setY(e.currentTarget.scrollTop)]
}

/** Albums and playlists: tinted header with big artwork, actions, track list. */
function Collection({ title, subtitle, onSubtitle, thumb, art, tracks, numbered, actions, localPlaylistId }) {
  const tint = useArtColor(thumb, '#5b3fa8')
  const [y, onScroll] = useScroll()
  const p = Math.min(1, y / 260)
  const min = Math.floor(tracks.reduce((s, t) => s + (t.duration || 0), 0) / 60)
  return (
    <div className="screen" onScroll={onScroll}>
      <BackButton />
      <div className="collection-head" style={{ '--tint': tint }}>
        <div className="collection-art" style={{ transform: `translateY(${y * 0.3}px) scale(${1 - p * 0.2})`, opacity: 1 - p * 0.9 }}>
          {art || <Artwork url={thumb} radius={18} className="square" />}
        </div>
        <div className="collection-info">
          <h2>{title}</h2>
          <button className={`collection-sub ${onSubtitle ? 'link' : ''}`} onClick={onSubtitle} disabled={!onSubtitle}>{subtitle}</button>
          <div className="dim small">{tracks.length === 1 ? '1 brano' : `${tracks.length} brani`}{min > 0 && ` • ${min} min`}</div>
          <div className="collection-actions">{actions}</div>
          <PlayShuffle tracks={tracks} />
        </div>
      </div>
      {tracks.map((t, i) => (
        <TrackRow key={`${i}-${t.id}`} t={t} index={numbered ? i + 1 : undefined} localPlaylistId={localPlaylistId}
          subtitle={numbered ? t.artist : undefined} onClick={() => player.play(tracks, i)} />
      ))}
    </div>
  )
}

export function AlbumScreen({ id }) {
  const state = useLoad(() => api.album(id), id)
  useLibrary()
  return (
    <Loading state={state}>{(a) => (
      <Collection title={a.title} subtitle={[a.artist, a.year].filter(Boolean).join(' • ')} thumb={a.thumb} tracks={a.tracks} numbered
        onSubtitle={a.artistId ? () => navigate({ name: 'artist', id: a.artistId }) : undefined}
        actions={<HeartButton saved={lib.hasAlbum(a.id)} onClick={() => lib.toggleAlbum({ id: a.id, title: a.title, artist: a.artist, artistId: a.artistId, year: a.year, thumb: a.thumb })} />} />
    )}</Loading>
  )
}

export function PlaylistScreen({ id }) {
  const state = useLoad(() => api.playlist(id), id)
  return (
    <Loading state={state}>{(p) => (
      <Collection title={p.title} subtitle={p.author} thumb={p.thumb} tracks={p.tracks}
        actions={<IconButton name="library_add" label="Salva in libreria" className="round"
          onClick={async () => { if (await lib.importPlaylist(p.title, p.tracks)) toast('Playlist salvata in libreria') }} />} />
    )}</Loading>
  )
}

export function LocalPlaylistScreen({ id }) {
  const data = useLibrary()
  const p = data.playlists.find((x) => x.id === id)
  const [confirm, setConfirm] = useState(false)
  if (!p) return <div className="screen"><BackButton /><EmptyState icon="queue_music" title="Playlist non trovata" text="Potrebbe essere stata eliminata." /></div>
  return (
    <>
      <Collection title={p.name} subtitle="La tua playlist" thumb={p.tracks[0]?.thumb} tracks={p.tracks} localPlaylistId={p.id}
        art={<Mosaic thumbs={p.tracks.map((t) => t.thumb).filter(Boolean)} radius={18} />}
        actions={<IconButton name="delete" label="Elimina playlist" className="round" onClick={() => setConfirm(true)} />} />
      <Dialog open={confirm} onClose={() => setConfirm(false)} title={`Eliminare "${p.name}"?`}
        actions={<><button className="text-btn dim" onClick={() => setConfirm(false)}>Annulla</button>
          <button className="text-btn" onClick={() => { setConfirm(false); goBack(); lib.deletePlaylist(p.id) }}>Elimina</button></>}>
        La playlist verrà eliminata. I brani restano nella libreria.
      </Dialog>
    </>
  )
}

export function ArtistScreen({ id }) {
  const state = useLoad(() => api.artist(id), id)
  useLibrary()
  const [y, onScroll] = useScroll()
  return (
    <Loading state={state}>{(a) => (
      <div className="screen" onScroll={onScroll}>
        <BackButton />
        <div className="artist-hero">
          <Artwork url={a.thumb} radius={0} icon="person" className="artist-img" style={{ transform: `translateY(${y * 0.45}px) scale(${1 + y / 2400})` }} />
          <div className="artist-fade" />
          <div className="artist-name">
            <h1>{a.name}</h1>
            <HeartButton saved={lib.hasArtist(a.id)} onClick={() => lib.toggleArtist({ id: a.id, name: a.name, thumb: a.thumb })} />
          </div>
        </div>
        {a.songs.length > 0 && <>
          <PlayShuffle tracks={a.songs} />
          <h3 className="section">Brani popolari</h3>
          {a.songs.map((t, i) => <TrackRow key={t.id} t={t} onClick={() => player.play(a.songs, i)} />)}
        </>}
        {[['Album', a.albums], ['Singoli ed EP', a.singles]].filter(([, l]) => l.length).map(([label, list]) => (
          <div key={label}>
            <h3 className="section">{label}</h3>
            <div className="row-scroll">
              {list.map((al) => <AlbumCard key={al.id} width={160} title={al.title} subtitle={al.year || ''} thumb={al.thumb} onClick={() => navigate({ name: 'album', id: al.id })} />)}
            </div>
          </div>
        ))}
        <div style={{ height: 24 }} />
      </div>
    )}</Loading>
  )
}

