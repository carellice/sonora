import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Artwork, HeartButton, Icon, IconButton, Sheet, TrackRow, useArtColor, useLibrary } from './components.jsx'
import { audioEl, closePlayer, fmt, lib, openPlayer, player, playerStore, uiStore, useStore } from './core.js'

/** Current time and duration of the audio element, polled while visible. */
export function usePosition() {
  const [p, set] = useState({ pos: 0, dur: 0 })
  useEffect(() => {
    const h = setInterval(() => set({ pos: audioEl.currentTime || 0, dur: Number.isFinite(audioEl.duration) ? audioEl.duration : 0 }), 250)
    return () => clearInterval(h)
  }, [])
  return p
}

function PlayPause({ big }) {
  const { playing, buffering } = useStore(playerStore)
  return (
    <motion.button className={`pp ${big ? 'big' : ''}`} whileTap={{ scale: 0.88 }} onClick={(e) => { e.stopPropagation(); player.toggle() }}
      aria-label={playing ? 'Pausa' : 'Riproduci'}>
      {buffering ? <span className="spinner" style={{ width: big ? 34 : 22, height: big ? 34 : 22 }} /> : (
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.span key={playing ? 'p' : 'r'} initial={{ scale: 0.4, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.4, opacity: 0 }}
            transition={{ duration: 0.15 }} style={{ display: 'flex' }}>
            <Icon name={playing ? 'pause' : 'play_arrow'} size={big ? 44 : 30} />
          </motion.span>
        </AnimatePresence>
      )}
    </motion.button>
  )
}

function Seek({ className = '' }) {
  const { pos, dur } = usePosition()
  const [drag, setDrag] = useState(null)
  const v = drag ?? (dur ? pos / dur : 0)
  return (
    <div className={`seek ${className}`}>
      <span>{fmt(drag != null ? drag * dur : pos) || '0:00'}</span>
      <input type="range" min={0} max={1} step={0.001} value={v} style={{ '--v': `${v * 100}%` }} aria-label="Posizione"
        onChange={(e) => setDrag(Number(e.target.value))}
        onPointerUp={() => { if (drag != null) { player.seek(drag * dur); setDrag(null) } }}
        onKeyUp={() => { if (drag != null) { player.seek(drag * dur); setDrag(null) } }} />
      <span>{dur ? fmt(dur) : '--:--'}</span>
    </div>
  )
}

function Controls({ big }) {
  const { shuffle, repeat } = useStore(playerStore)
  const s = big ? 42 : 28
  return (
    <div className={`controls ${big ? 'big' : ''}`}>
      <IconButton name="shuffle" label="Casuale" active={shuffle} onClick={player.toggleShuffle} />
      <IconButton name="skip_previous" label="Precedente" size={s} onClick={player.previous} />
      <PlayPause big={big} />
      <IconButton name="skip_next" label="Successivo" size={s} onClick={player.next} />
      <IconButton name={repeat === 'one' ? 'repeat_one' : 'repeat'} label="Ripeti" active={repeat !== 'off'} onClick={player.cycleRepeat} />
    </div>
  )
}

/** Phone: floating card above the bottom bar. */
export function MiniPlayer() {
  const { current } = useStore(playerStore)
  const tint = useArtColor(current?.thumb)
  const { pos, dur } = usePosition()
  return (
    <AnimatePresence>
      {current && (
        <motion.div className="mini" style={{ '--tint': tint }} initial={{ y: 90, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 90, opacity: 0 }}
          transition={{ type: 'spring', stiffness: 420, damping: 30 }} whileTap={{ scale: 0.98 }} onClick={openPlayer}
          drag="y" dragConstraints={{ top: 0, bottom: 0 }} dragElastic={0.2} onDragEnd={(_, i) => i.offset.y < -30 && openPlayer()}>
          <AnimatePresence mode="popLayout" initial={false}>
            <motion.div key={current.id} initial={{ scale: 0.8, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ opacity: 0 }}>
              <Artwork url={current.thumb} size={44} />
            </motion.div>
          </AnimatePresence>
          <div className="mini-text">
            <div className="marquee"><span>{current.title}</span></div>
            <div className="mini-sub">{current.artist}</div>
          </div>
          <PlayPause />
          <IconButton name="skip_next" label="Successivo" onClick={player.next} />
          <div className="mini-progress"><div style={{ width: `${dur ? (pos / dur) * 100 : 0}%` }} /></div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

/** Desktop: full-width bar at the bottom. */
export function PlayerBar() {
  const { current, volume } = useStore(playerStore)
  useLibrary()
  if (!current) return <div className="bar empty-bar"><span className="dim">Scegli qualcosa da ascoltare</span></div>
  return (
    <div className="bar">
      <div className="bar-left" onClick={openPlayer}>
        <Artwork url={current.thumb} size={56} />
        <div className="bar-text">
          <div className="track-title">{current.title}</div>
          <div className="track-sub">{current.artist}</div>
        </div>
        <HeartButton round={false} saved={lib.hasSong(current.id)} onClick={() => lib.toggleSong(current)} />
      </div>
      <div className="bar-center">
        <Controls />
        <Seek />
      </div>
      <div className="bar-right">
        <IconButton name="queue_music" label="Prossimi brani" onClick={() => uiStore.set({ queue: true })} />
        <Icon name="volume_up" className="dim" />
        <input type="range" className="volume" min={0} max={1} step={0.01} value={volume} style={{ '--v': `${volume * 100}%` }}
          aria-label="Volume" onChange={(e) => player.setVolume(Number(e.target.value))} />
        <IconButton name="open_in_full" label="Schermo intero" onClick={openPlayer} />
      </div>
    </div>
  )
}

export function FullPlayer() {
  const { player: open } = useStore(uiStore)
  const { current, playing } = useStore(playerStore)
  useLibrary()
  const tint = useArtColor(current?.thumb)
  useEffect(() => { if (!current && open) closePlayer() }, [current, open])
  return (
    <AnimatePresence>
      {open && current && (
        <motion.div className="full" style={{ '--tint': tint }} initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }}
          transition={{ type: 'spring', stiffness: 380, damping: 40 }}
          drag="y" dragConstraints={{ top: 0, bottom: 0 }} dragElastic={{ top: 0, bottom: 0.7 }}
          onDragEnd={(_, i) => { if (i.offset.y > 160 || i.velocity.y > 700) closePlayer() }}>
          <div className="full-bg" style={{ backgroundImage: current.thumb ? `url(${current.thumb})` : undefined }} />
          <div className="full-inner">
            <div className="full-top">
              <IconButton name="keyboard_arrow_down" label="Chiudi" size={32} onClick={closePlayer} />
              <div className="full-top-text">
                <div className="caps">IN RIPRODUZIONE</div>
                <div className="full-top-album">{current.album || current.artist}</div>
              </div>
              <IconButton name="more_vert" label="Opzioni" onClick={() => uiStore.set({ menu: { track: current } })} />
            </div>
            <div className="full-body">
              <div className="full-art-wrap">
                <AnimatePresence mode="popLayout" initial={false}>
                  <motion.div key={current.id} className="full-art" initial={{ x: 80, opacity: 0 }} animate={{ x: 0, opacity: 1, scale: playing ? 1 : 0.86 }}
                    exit={{ x: -80, opacity: 0 }} transition={{ type: 'spring', stiffness: 260, damping: 22 }}>
                    <Artwork url={current.thumb} radius={22} className="square" />
                  </motion.div>
                </AnimatePresence>
              </div>
              <div className="full-panel">
                <div className="full-title-row">
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div className="marquee full-title"><span>{current.title}</span></div>
                    <div className="full-artist">{current.artist}</div>
                  </div>
                  <HeartButton round={false} size={28} saved={lib.hasSong(current.id)} onClick={() => lib.toggleSong(current)} />
                </div>
                <Seek className="full-seek" />
                <Controls big />
                <button className="chip" onClick={() => uiStore.set({ queue: true })}><Icon name="queue_music" size={18} /> Prossimi brani</button>
              </div>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export function QueueSheet() {
  const { queue } = useStore(uiStore)
  const ps = useStore(playerStore)
  const list = queue ? player.upNext() : []
  return (
    <Sheet open={queue} onClose={() => uiStore.set({ queue: false })}>
      <h3 className="sheet-title">Prossimi brani</h3>
      {list.length === 0 && <p className="dim" style={{ padding: '8px 24px' }}>La coda è vuota</p>}
      <div className="sheet-scroll">
        {list.map(({ pos, t }) => <TrackRow key={`${pos}-${t.id}-${ps.pos}`} t={t} onClick={() => player.jumpTo(pos)} />)}
      </div>
    </Sheet>
  )
}
