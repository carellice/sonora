# Sonora

La tua musica da YouTube Music, in streaming **dal tuo server di casa** (CasaOS, Raspberry Pi o
qualsiasi Linux con Docker) verso telefono e browser.

- **Web app** (PWA) con lo stesso stile dell'app Android, adattata anche a PC e tablet: si apre
  dall'icona di CasaOS e si installa come app dal browser.
- **App Android** nativa, scaricabile direttamente dal tuo server.
- **Profili** separati: ognuno ha la sua libreria (brani, album, artisti) e le sue playlist.
- Ricerca di brani, album, artisti e playlist; radio automatica dopo un brano; coda, ripetizione,
  riproduzione casuale; controlli dalla schermata di blocco.
- Accesso da fuori casa con **Tailscale** (HTTPS vero, solo i tuoi dispositivi).

## Installazione (un solo comando)

Sul server, via SSH:

```bash
curl -fsSL https://raw.githubusercontent.com/carellice/sonora/main/casaos/installa.sh | sudo bash
```

Lo script ti guida: controlla Docker e CasaOS, chiede cartella dati, porta (predefinita `8110`) e
fuso orario, controlla Tailscale e, se manca, si offre di installarlo e collegarlo; poi costruisce
Sonora, la aggiunge alla dashboard di CasaOS, la pubblica su Tailscale e ti chiede se vuoi gli
aggiornamenti automatici ogni notte.

**Repository privato.** Finché il repository è privato serve un token GitHub di sola lettura
(github.com → Settings → Developer settings → Fine-grained tokens → accesso a questo repository,
*Contents: Read-only*). Scarica lo script con il token, che poi ti verrà chiesto e salvato in
`/etc/sonora/github-token` per gli aggiornamenti:

```bash
curl -fsSL -H "Authorization: Bearer IL_TUO_TOKEN" https://raw.githubusercontent.com/carellice/sonora/main/casaos/installa.sh | sudo bash
```

Rilanciare lo script aggiorna Sonora mantenendo dati e impostazioni.

## Usarla

- **Browser:** apri l'indirizzo mostrato alla fine dell'installazione (in casa `http://IP-DEL-SERVER:8110`,
  con Tailscale `https://nome-server.tuarete.ts.net:PORTA`). Chrome ⋮ → *Installa app*,
  Safari *Condividi* → *Aggiungi alla schermata Home*.
- **Android:** scarica l'APK da `INDIRIZZO/sonora.apk` oppure dalla web app (*Impostazioni* →
  *Scarica l'app Android*). Al primo avvio l'app chiede l'indirizzo del server. Quando sul server
  arriva una versione nuova, l'app lo segnala e la scarica da lì.
- **Profili:** al primo accesso crea il tuo profilo con "+"; tieni premuto per eliminarne uno
  (la sua libreria resta sul server come file `.deleted`).

L'audio viene preso da YouTube dal server e inviato al telefono/browser: il telefono non parla mai
direttamente con YouTube. La prima volta che ascolti un brano servono circa 10 secondi; i brani
successivi in coda vengono preparati in anticipo e partono subito.

## Aggiornamenti

```bash
sudo sonora-aggiorna              # aggiorna se c'è una versione nuova
sudo sonora-aggiorna --controlla  # dice soltanto se c'è una versione nuova
sudo sonora-aggiorna --forza      # ricostruisce anche se la versione è la stessa
```

Se all'installazione hai scelto gli aggiornamenti automatici, il controllo avviene ogni notte verso
le 4 (`journalctl -u sonora-aggiorna` per lo storico, `systemctl disable --now sonora-aggiorna.timer`
per spegnerli). Prima di ogni aggiornamento i dati vengono copiati in `DATI/backup/`; se la nuova
versione non parte, si torna da soli a quella di prima. Una volta a settimana viene aggiornato anche
**yt-dlp**, che deve stare al passo con i cambiamenti di YouTube.

## Rilasciare una nuova versione

1. Cambia `version` in `server/package.json` (es. `1.1.0` → `1.2.0`): è la versione di tutto
   (server, web app e APK).
2. Fai push su `main`.
3. GitHub Actions costruisce l'APK firmato e crea la release `v1.2.0` con `Sonora.apk`.
4. I server si aggiornano la notte dopo (o subito con `sudo sonora-aggiorna`) e scaricano il nuovo
   APK; l'app Android propone l'aggiornamento.

La firma dell'APK usa una chiave salvata nei *secrets* del repository
(`SONORA_KEYSTORE_BASE64`, `SONORA_KEYSTORE_PASSWORD`, `SONORA_KEY_PASSWORD`): deve restare sempre
la stessa, altrimenti Android rifiuta di installare un aggiornamento sopra la versione precedente.
Conservane una copia di backup.

## Struttura

```
server/    Node 22: API (YouTube Music con youtubei.js, streaming con yt-dlp), profili, web app, APK
web/       web app React + Vite + framer-motion (build in server/public)
android/   app Android (Kotlin, Jetpack Compose, Media3)
casaos/    installa.sh e aggiorna.sh (sonora-aggiorna)
Dockerfile immagine unica: build della web app + server
```

Sviluppo: `cd server && npm i && DATA_DIR=./data node server.mjs` (serve `yt-dlp` nel PATH per lo
streaming), in un altro terminale `cd web && npm i && npm run dev`. App Android: apri `android/`
in Android Studio.
