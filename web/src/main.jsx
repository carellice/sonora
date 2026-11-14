import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import './styles.css'
import './skeu.css'
import './metro.css'

// skin chosen in Impostazioni → Stile grafica
const skin = localStorage.getItem('sonora.skin')
document.documentElement.classList.toggle('skeu', skin === 'skeu')
document.documentElement.classList.toggle('metro', skin === 'metro')

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>)

if ('serviceWorker' in navigator && location.hostname !== 'localhost') {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => {}))
}
