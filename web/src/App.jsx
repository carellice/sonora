import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Artwork, Dialog, Icon, Mosaic, NameDialog, SegmentedTabs, Sheet, useLibrary } from './components.jsx'
import { FullPlayer, MiniPlayer, PlayerBar, QueueSheet } from './player.jsx'
import {
  AlbumScreen, ArtistScreen, LibraryScreen, LocalPlaylistScreen, PlaylistScreen, ProfileScreen, SearchScreen, profileGradient,
} from './screens.jsx'
import {
  api, closePlayer, leaveProfile, lib, navStore, navigate, player, profile, sessionStore, setTab, toast, toastStore, uiStore, useStore,
} from './core.js'

function useWide() {
  const q = '(min-width: 900px)'
  const [wide, set] = useState(() => matchMedia(q).matches)
  useEffect(() => {
    const m = matchMedia(q)
    const on = () => set(m.matches)
    m.addEventListener('change', on)
    return () => m.removeEventListener('change', on)
  }, [])
  return wide
}

export default function App() {
  const { user } = useStore(sessionStore)
  useEffect(() => { if (user) lib.refresh() }, [user])
  return (
    <>
      <AnimatePresence mode="wait">
        <motion.div key={user ? 'app' : 'profiles'} className="root" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.25 }}>
          {user ? <Shell /> : <ProfileScreen />}
        </motion.div>
      </AnimatePresence>
      <Toast />
    </>
  )
}

function Screen({ route, onSettings }) {
  switch (route.name) {
    case 'search': return <SearchScreen />
    case 'album': return <AlbumScreen id={route.id} />
    case 'artist': return <ArtistScreen id={route.id} />
    case 'playlist': return <PlaylistScreen id={route.id} />
    case 'local': return <LocalPlaylistScreen id={route.id} />
    default: return <LibraryScreen onSettings={onSettings} />
  }
}

// Nothing: pages only fade, quick ease-out, no movement
const FADE = { initial: { opacity: 0 }, animate: { opacity: 1 }, exit: { opacity: 0 }, transition: { duration: 0.2, ease: [0.25, 0.1, 0.25, 1] } }
const SLIDE = { initial: { opacity: 0, x: 24 }, animate: { opacity: 1, x: 0 }, exit: { opacity: 0 }, transition: { duration: 0.22 } }
// Modern UI: pages swing in like a turnstile, hinged on the left edge
const TURNSTILE = {
  initial: { opacity: 0, rotateY: -75, transformPerspective: 1200, originX: 0 },
  animate: { opacity: 1, rotateY: 0, transition: { duration: 0.38, ease: [0.1, 0.9, 0.2, 1] } },
  exit: { opacity: 0, rotateY: 60, transformPerspective: 1200, originX: 0, transition: { duration: 0.15, ease: 'easeIn' } },
}

function Shell() {
  const wide = useWide()
  const { stack } = useStore(navStore)
  const route = stack[stack.length - 1]
  const tab = stack[0].name
  const [settings, setSettings] = useState(false)
  return (
    <div className={`shell ${wide ? 'wide' : 'narrow'}`}>
      {wide && <Sidebar tab={tab} route={route} onSettings={() => setSettings(true)} />}
      <main className="main">
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.div key={`${stack.length}-${route.name}-${route.id || ''}`} className="page"
            {...(document.documentElement.classList.contains('metro') ? TURNSTILE : document.documentElement.classList.contains('nothing') ? FADE : SLIDE)}>
            <Screen route={route} onSettings={() => setSettings(true)} />
          </motion.div>
        </AnimatePresence>
      </main>
      {wide ? <PlayerBar /> : (
        <div className="bottom">
          <MiniPlayer />
          <nav className="tabs">
            {[['library', 'Libreria', 'library_music'], ['search', 'Cerca', 'search']].map(([name, label, icon]) => (
              <button key={name} className={`tab ${tab === name ? 'on' : ''}`} onClick={() => setTab(name)}>
                <span className="tab-icon">
                  {tab === name && <motion.span layoutId="tab-pill" className="tab-pill" transition={{ type: 'spring', stiffness: 500, damping: 34 }} />}
                  <Icon name={icon} />
                </span>
                {label}
              </button>
            ))}
          </nav>
        </div>
      )}
      <FullPlayer />
      <QueueSheet />
      <TrackMenu />
      <AddToPlaylist />
      <Settings open={settings} onClose={() => setSettings(false)} />
    </div>
  )
}

function Sidebar({ tab, route, onSettings }) {
  const data = useLibrary()
  const [creating, setCreating] = useState(false)
  const [me, setMe] = useState(null)
  useEffect(() => { api.users().then((l) => setMe(l.find((u) => u.id === profile.id))).catch(() => {}) }, [])
  return (
    <aside className="side">
      <div className="logo"><img src="/icon-192.png" alt="" /> Sonora</div>
      {[['library', 'Libreria', 'library_music'], ['search', 'Cerca', 'search']].map(([name, label, icon]) => (
        <button key={name} className={`side-item ${tab === name ? 'on' : ''}`} onClick={() => setTab(name)}>
          {tab === name && <motion.span layoutId="side-pill" className="side-pill" transition={{ type: 'spring', stiffness: 500, damping: 36 }} />}
          <Icon name={icon} /><span>{label}</span>
        </button>
      ))}
      <div className="side-title">
        Le tue playlist
        <button className="icon-btn dim" aria-label="Nuova playlist" title="Nuova playlist" onClick={() => setCreating(true)}><Icon name="add" size={20} /></button>
      </div>
      <div className="side-list">
        {data.playlists.map((p) => (
          <button key={p.id} className={`side-pl ${route.name === 'local' && route.id === p.id ? 'on' : ''}`} onClick={() => navigate({ name: 'local', id: p.id })}>
            <Mosaic thumbs={p.tracks.map((t) => t.thumb).filter(Boolean)} size={40} radius={8} />
            <span>{p.name}</span>
          </button>
        ))}
        {data.playlists.length === 0 && <p className="dim small" style={{ padding: '4px 12px' }}>Ancora nessuna playlist</p>}
      </div>
      <button className="side-me" onClick={onSettings}>
        <span className="me-avatar" style={{ background: profileGradient(profile.id) }}>{(me?.name || '?').slice(0, 1).toUpperCase()}</span>
        <span>{me?.name || 'Profilo'}</span>
        <Icon name="settings" size={20} className="dim" />
      </button>
      <NameDialog open={creating} title="Nuova playlist" onClose={() => setCreating(false)} onDone={(n) => { setCreating(false); lib.createPlaylist(n) }} />
    </aside>
  )
}

function MenuItem({ icon, children, onClick }) {
  return <button className="menu-item" onClick={onClick}><Icon name={icon} />{children}</button>
}

function TrackMenu() {
  const { menu } = useStore(uiStore)
  useLibrary()
  const close = () => uiStore.set({ menu: null })
  const t = menu?.track
  const go = (route) => { close(); closePlayer(); setTimeout(() => navigate(route), 50) }
  return (
    <Sheet open={!!menu} onClose={close}>
      {t && <>
        <div className="menu-head">
          <Artwork url={t.thumb} size={56} />
          <div className="track-text"><div className="track-title">{t.title}</div><div className="track-sub">{t.artist}</div></div>
        </div>
        <MenuItem icon="playlist_play" onClick={() => { player.playNext(t); toast('Riprodotto dopo il brano attuale'); close() }}>Riproduci dopo</MenuItem>
        <MenuItem icon="queue_music" onClick={() => { player.enqueue(t); toast('Aggiunto in coda'); close() }}>Aggiungi in coda</MenuItem>
        <MenuItem icon="radio" onClick={() => { player.playRadio(t); close() }}>Avvia radio del brano</MenuItem>
        <MenuItem icon={lib.hasSong(t.id) ? 'favorite' : 'favorite_border'} onClick={() => { lib.toggleSong(t); close() }}>
          {lib.hasSong(t.id) ? 'Rimuovi dalla libreria' : 'Salva in libreria'}
        </MenuItem>
        <MenuItem icon="playlist_add" onClick={() => { close(); uiStore.set({ addTo: t }) }}>Aggiungi a playlist</MenuItem>
        {menu.localPlaylistId && <MenuItem icon="remove_circle_outline" onClick={() => { lib.removeFromPlaylist(menu.localPlaylistId, t.id); close() }}>Rimuovi da questa playlist</MenuItem>}
        {t.albumId && <MenuItem icon="album" onClick={() => go({ name: 'album', id: t.albumId })}>Vai all'album</MenuItem>}
        {t.artistId && <MenuItem icon="person" onClick={() => go({ name: 'artist', id: t.artistId })}>Vai all'artista</MenuItem>}
        <div style={{ height: 16 }} />
      </>}
    </Sheet>
  )
}

function AddToPlaylist() {
  const { addTo } = useStore(uiStore)
  const data = useLibrary()
  const [creating, setCreating] = useState(false)
  const close = () => uiStore.set({ addTo: null })
  const add = async (pid, name) => { close(); if (await lib.addToPlaylist(pid, addTo)) toast(`Aggiunto a "${name}"`) }
  return (
    <>
      <Dialog open={!!addTo && !creating} onClose={close} title="Aggiungi a playlist" actions={<button className="text-btn dim" onClick={close}>Chiudi</button>}>
        <div className="pick-list">
          <button className="pick accent" onClick={() => setCreating(true)}><Icon name="add" /> Nuova playlist</button>
          {data.playlists.map((p) => (
            <button key={p.id} className="pick" onClick={() => add(p.id, p.name)}>
              <Mosaic thumbs={p.tracks.map((t) => t.thumb).filter(Boolean)} size={40} radius={8} />{p.name}
            </button>
          ))}
        </div>
      </Dialog>
      <NameDialog open={!!addTo && creating} title="Nuova playlist" onClose={() => { setCreating(false); close() }}
        onDone={async (n) => {
          const t = addTo
          setCreating(false); close()
          const pid = await lib.newPlaylist(n)
          if (pid && await lib.addToPlaylist(pid, t)) toast(`Aggiunto a "${n}"`)
        }} />
    </>
  )
}

const SKINS = ['classic', 'skeu', 'metro', 'nothing'] // the class on <html>; "classic" has no rules

function Settings({ open, onClose }) {
  const [info, setInfo] = useState(null)
  const [skin, setSkin] = useState(() => localStorage.getItem('sonora.skin') || 'classic')
  useEffect(() => { if (open) api.info().then(setInfo).catch(() => {}) }, [open])
  const pickSkin = (i) => {
    const s = SKINS[i]
    setSkin(s)
    localStorage.setItem('sonora.skin', s)
    for (const k of SKINS) document.documentElement.classList.toggle(k, k === s)
  }
  return (
    <Dialog open={open} onClose={onClose} title="Impostazioni" actions={<button className="text-btn dim" onClick={onClose}>Chiudi</button>}>
      <div className="settings">
        <b>Stile grafica</b>
        <SegmentedTabs id="skin" tabs={['Moderno', 'Skeuomorfismo', 'Modern UI', 'Nothing']} selected={Math.max(0, SKINS.indexOf(skin))} onSelect={pickSkin} />
        <div style={{ height: 8 }} />
        <a className={`apk ${info?.apk ? '' : 'disabled'}`} href={info?.apk ? '/sonora.apk' : undefined} download>
          <Icon name="android" size={32} />
          <span>
            <b>Scarica l'app Android</b>
            <small>{info?.apk ? `Versione ${info.apk.version} · ${(info.apk.size / 1048576).toFixed(1)} MB` : 'Non ancora disponibile sul server'}</small>
          </span>
          <Icon name="download" />
        </a>
        <button className="menu-item" onClick={() => { onClose(); player.stop(); leaveProfile() }}><Icon name="swap_horiz" />Cambia profilo</button>
        <p className="dim small">Sonora {info?.version || ''} · {location.host}</p>
      </div>
    </Dialog>
  )
}

function Toast() {
  const { msg, key } = useStore(toastStore)
  return (
    <AnimatePresence>
      {msg && <motion.div key={key} className="toast" initial={{ y: 30, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 30, opacity: 0 }}>{msg}</motion.div>}
    </AnimatePresence>
  )
}
