import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const outDir = '../server/public'

/** Stamps the service worker with this build and the files to keep offline. */
function serviceWorker() {
  return {
    name: 'sonora-sw',
    apply: 'build',
    closeBundle() {
      const assets = readdirSync(join(outDir, 'assets')).map((f) => `/assets/${f}`)
      const files = ['/manifest.webmanifest', '/icon-192.png', '/icon-512.png', ...assets]
      const file = join(outDir, 'sw.js')
      writeFileSync(file, readFileSync(file, 'utf8').replace('__BUILD__', String(Date.now())).replace('__FILES__', JSON.stringify(files)))
    },
  }
}

export default defineConfig({
  plugins: [react(), serviceWorker()],
  build: { outDir, emptyOutDir: true, target: 'es2020', chunkSizeWarningLimit: 1500 },
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080', '/sonora.apk': 'http://localhost:8080' } },
})
