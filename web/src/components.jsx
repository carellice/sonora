import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { uiIcons } from './icons.gen.ts'
import { artColor, fmt, lib, libStore, player, playerStore, uiStore, useStore } from './core.js'

export const spring = { type: 'spring', stiffness: 500, damping: 32 }

export function Icon({ name, size = 24, className, style }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="currentColor" className={className} style={style}
      aria-hidden="true" dangerouslySetInnerHTML={{ __html: uiIcons[name] }} />
  )
}

export function IconButton({ name, label, onClick, size = 24, className = '', active, style }) {
  return (
    <motion.button whileTap={{ scale: 0.88 }} className={`icon-btn ${active ? 'active' : ''} ${className}`}
      aria-label={label} title={label} onClick={(e) => { e.stopPropagation(); onClick?.(e) }} style={style}>
      <Icon name={name} size={size} />
    </motion.button>
  )
}

export function Artwork({ url, size, radius = 10, icon = 'music_note', className = '', style }) {
  const [loaded, setLoaded] = useState(false)
  useEffect(() => setLoaded(false), [url])
  return (
    <div className={`art ${className}`} style={{ width: size, height: size, borderRadius: radius, ...style }}>
      <Icon name={icon} className="art-ph" />
      {url && <img src={url} alt="" loading="lazy" onLoad={() => setLoaded(true)} style={{ opacity: loaded ? 1 : 0 }} />}
    </div>
  )
}

/** Dark saturated color from the artwork, animated by CSS transitions where it is used. */
export function useArtColor(url, fallback = '#221F2E') {
  const [c, setC] = useState(fallback)
  useEffect(() => {
    let alive = true
    artColor(url).then((v) => alive && setC(v || fallback))
    return () => { alive = false }
  }, [url, fallback])
  return c
}

export function EqBars({ playing, color = 'var(--accent)' }) {
  return (
    <span className={`eq ${playing ? 'on' : ''}`} style={{ color }}>
      <i /><i /><i />
    </span>
  )
}

export function TrackRow({ t, onClick, index, subtitle, localPlaylistId }) {
  const { current, playing } = useStore(playerStore)
  const isCur = current?.id === t.id
  const sub = subtitle ?? [t.artist, t.album].filter(Boolean).join(' • ')
  const openMenu = (e) => { e.preventDefault(); uiStore.set({ menu: { track: t, localPlaylistId } }) }
  return (
    <motion.div className={`track ${isCur ? 'current' : ''}`} whileTap={{ scale: 0.97 }} onClick={onClick} onContextMenu={openMenu}>
      {index != null ? (
        <div className="track-index">{isCur ? <EqBars playing={playing} /> : index}</div>
      ) : (
        <div className="track-art">
          <Artwork url={t.thumb} size={50} />
          {isCur && <div className="track-art-eq"><EqBars playing={playing} color="#fff" /></div>}
        </div>
      )}
      <div className="track-text">
        <div className="track-title">{t.title}</div>
        <div className="track-sub">{[sub, fmt(t.duration)].filter(Boolean).join(' • ')}</div>
      </div>
      <IconButton name="more_vert" label="Opzioni" onClick={openMenu} className="dim" />
    </motion.div>
  )
}

export function AlbumCard({ title, subtitle, thumb, onClick, width }) {
  return (
    <motion.div className="card" style={width ? { width, flex: 'none' } : undefined} whileTap={{ scale: 0.96 }} whileHover={{ y: -3 }} onClick={onClick}>
      <Artwork url={thumb} radius={12} className="square" />
      <div className="card-title">{title}</div>
      <div className="card-sub">{subtitle}</div>
    </motion.div>
  )
}

export function Avatar({ name, thumb, size = 56 }) {
  if (thumb) return <Artwork url={thumb} size={size} radius={size} icon="person" />
  return <div className="avatar" style={{ width: size, height: size, fontSize: size * 0.4 }}>{(name || '?').slice(0, 1).toUpperCase()}</div>
}

export function ArtistRow({ a, onClick }) {
  return (
    <motion.div className="track" whileTap={{ scale: 0.97 }} onClick={onClick}>
      <Avatar name={a.name} thumb={a.thumb} />
      <div className="track-text" style={{ marginLeft: 6 }}>
        <div className="track-title" style={{ fontSize: 16 }}>{a.name}</div>
        <div className="track-sub">Artista</div>
      </div>
    </motion.div>
  )
}

export function SearchField({ value, onChange, placeholder, big, autoFocus }) {
  return (
    <label className={`search ${big ? 'big' : ''} ${value ? 'filled' : ''}`}>
      <Icon name="search" size={20} className="dim" />
      <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} autoFocus={autoFocus} enterKeyHint="search" />
      {value && <IconButton name="close" label="Cancella" size={20} className="dim" onClick={() => onChange('')} />}
    </label>
  )
}

export function SegmentedTabs({ tabs, selected, onSelect, id }) {
  return (
    <div className="seg">
      {tabs.map((t, i) => (
        <button key={t} className={`seg-item ${i === selected ? 'on' : ''}`} onClick={() => onSelect(i)}>
          {i === selected && <motion.span layoutId={`seg-${id}`} className="seg-pill" transition={{ type: 'spring', stiffness: 500, damping: 34 }} />}
          <span className="seg-label">{t}</span>
        </button>
      ))}
    </div>
  )
}

export function GradientButton({ icon, children, onClick, className = '' }) {
  return (
    <motion.button whileTap={{ scale: 0.95 }} whileHover={{ scale: 1.02 }} className={`btn grad ${className}`} onClick={onClick}>
      {icon && <Icon name={icon} />}{children}
    </motion.button>
  )
}
export function SoftButton({ icon, children, onClick, className = '' }) {
  return (
    <motion.button whileTap={{ scale: 0.95 }} whileHover={{ scale: 1.02 }} className={`btn soft ${className}`} onClick={onClick}>
      {icon && <Icon name={icon} />}{children}
    </motion.button>
  )
}

export function PlayShuffle({ tracks }) {
  return (
    <div className="play-row">
      <GradientButton icon="play_arrow" onClick={() => player.play(tracks)}>Riproduci</GradientButton>
      <SoftButton icon="shuffle" onClick={() => player.play(tracks, 0, true)}>Casuale</SoftButton>
    </div>
  )
}

export function EmptyState({ icon, title, text, children }) {
  return (
    <motion.div className="empty" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <div className="empty-icon"><Icon name={icon} size={38} /></div>
      <div className="empty-title">{title}</div>
      <div className="empty-text">{text}</div>
      {children}
    </motion.div>
  )
}

export function HeartButton({ saved, onClick, round = true, size = 24 }) {
  return (
    <motion.button className={`icon-btn ${round ? 'round' : ''}`} whileTap={{ scale: 0.8 }} onClick={(e) => { e.stopPropagation(); onClick() }}
      aria-label={saved ? 'Rimuovi dalla libreria' : 'Salva in libreria'} title={saved ? 'Rimuovi dalla libreria' : 'Salva in libreria'}>
      <motion.span animate={{ scale: saved ? 1.15 : 1 }} transition={{ type: 'spring', stiffness: 400, damping: 8 }} style={{ display: 'flex', color: saved ? 'var(--accent)' : '#fff' }}>
        <Icon name={saved ? 'favorite' : 'favorite_border'} size={size} />
      </motion.span>
    </motion.button>
  )
}

export function Mosaic({ thumbs, size, radius = 12 }) {
  const t = [...new Set(thumbs)]
  if (t.length < 4) return <Artwork url={t[0]} size={size} radius={radius} icon="queue_music" className={size ? '' : 'square'} />
  return (
    <div className={`mosaic ${size ? '' : 'square'}`} style={{ width: size, height: size, borderRadius: radius }}>
      {t.slice(0, 4).map((u) => <img key={u} src={u} alt="" />)}
    </div>
  )
}

export const Spinner = ({ size = 36 }) => <div className="spinner" style={{ width: size, height: size }} />

export function Dialog({ open, onClose, title, children, actions }) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="scrim center" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
          <motion.div className="dialog" initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }}
            transition={spring} onClick={(e) => e.stopPropagation()}>
            {title && <div className="dialog-title">{title}</div>}
            <div className="dialog-body">{children}</div>
            {actions && <div className="dialog-actions">{actions}</div>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export function Sheet({ open, onClose, children }) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
          <motion.div className="sheet" initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }}
            transition={{ type: 'spring', stiffness: 420, damping: 40 }} drag="y" dragConstraints={{ top: 0, bottom: 0 }} dragElastic={{ top: 0, bottom: 0.6 }}
            onDragEnd={(_, i) => { if (i.offset.y > 120 || i.velocity.y > 600) onClose() }} onClick={(e) => e.stopPropagation()}>
            <div className="sheet-handle" />
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export function NameDialog({ open, title, action = 'Crea', placeholder = 'Nome', onClose, onDone }) {
  const [name, setName] = useState('')
  useEffect(() => { if (open) setName('') }, [open])
  const done = () => { if (name.trim()) onDone(name.trim()) }
  return (
    <Dialog open={open} onClose={onClose} title={title}
      actions={<><button className="text-btn dim" onClick={onClose}>Annulla</button><button className="text-btn" onClick={done}>{action}</button></>}>
      <input className="field" autoFocus value={name} placeholder={placeholder} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && done()} />
    </Dialog>
  )
}

export function useLibrary() { return useStore(libStore).data }
