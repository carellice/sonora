#!/usr/bin/env bash
# Installazione guidata di Sonora su CasaOS (o su qualsiasi server Linux con Docker).
#
# Sul server, via SSH:
#   curl -fsSL https://raw.githubusercontent.com/carellice/sonora/main/casaos/installa.sh | sudo bash
# Finché il repository è privato serve un token GitHub (lettura) e lo script va scaricato con quello:
#   curl -fsSL -H "Authorization: Bearer <token>" https://raw.githubusercontent.com/carellice/sonora/main/casaos/installa.sh | sudo bash
# oppure, dalla cartella del repository:
#   sudo bash casaos/installa.sh
#
# Rilanciandolo si aggiorna Sonora all'ultima versione: i dati restano dove sono.
# Installa anche il comando "sonora-aggiorna" e, se vuoi, un timer che aggiorna Sonora ogni notte.

set -euo pipefail

REPO="carellice/sonora"
BRANCH="${SONORA_BRANCH:-main}"
APP="sonora"
IMAGE="sonora:latest"
CASAOS_APPS="/var/lib/casaos/apps"
TOKEN_FILE="/etc/sonora/github-token"
DEFAULT_PORT=8110

# --- aspetto -----------------------------------------------------------------------------------

if [ -t 1 ]; then
  B=$'\e[1m'; D=$'\e[2m'; R=$'\e[0m'; V=$'\e[35m'; G=$'\e[32m'; Y=$'\e[33m'; E=$'\e[31m'
else
  B=""; D=""; R=""; V=""; G=""; Y=""; E=""
fi
step() { printf "\n${V}${B}==> %s${R}\n" "$1"; }
info() { printf "    %s\n" "$1"; }
ok() { printf "    ${G}✓${R} %s\n" "$1"; }
warn() { printf "    ${Y}!${R} %s\n" "$1"; }
fail() { printf "\n${E}${B}ERRORE:${R} %s\n" "$1" >&2; exit 1; }

# Le domande leggono sempre dal terminale, anche quando lo script arriva da "curl | bash".
TTY=/dev/tty
[ -r "$TTY" ] || fail "Serve un terminale interattivo: lancia lo script da SSH o dal terminale di CasaOS."

ask() { # ask "domanda" "predefinito" -> REPLY
  local question="$1" default="${2:-}"
  if [ -n "$default" ]; then printf "    %s ${D}[%s]${R}: " "$question" "$default" >"$TTY"; else printf "    %s: " "$question" >"$TTY"; fi
  IFS= read -r REPLY <"$TTY" || true
  REPLY="${REPLY:-$default}"
}

ask_yes() { # ask_yes "domanda" s|n
  local default="${2:-s}" hint="S/n"
  [ "$default" = "n" ] && hint="s/N"
  printf "    %s ${D}[%s]${R}: " "$1" "$hint" >"$TTY"
  IFS= read -r REPLY <"$TTY" || true
  REPLY="${REPLY:-$default}"
  [[ "$REPLY" =~ ^[sSyY] ]]
}

printf "${V}${B}"
cat <<'EOF'

    ____   ___   ____      _      ____
   / ___| / _ \ / ___|    / \    / ___|
  | |    | | | |\___ \   / _ \  | |
  | |___ | |_| | ___) | / ___ \ | |___
   \____| \___/ |____/ /_/   \_\ \____|

  CasaOS App Converter · installazione di Sonora
EOF
printf "${R}\n"
info "Sonora: la tua musica da YouTube Music, in streaming dal tuo server su telefono e browser."
info "Ti guido passo passo. Premi Invio per accettare il valore tra parentesi."

# --- controlli ---------------------------------------------------------------------------------

step "Controlli"
[ "$(id -u)" -eq 0 ] || fail "Lancia lo script come amministratore: sudo bash installa.sh"
command -v curl >/dev/null || fail "Serve curl."
command -v docker >/dev/null || fail "Docker non trovato. CasaOS lo installa da sé: sei sul server giusto?"
docker info >/dev/null 2>&1 || fail "Docker non risponde. Prova: systemctl start docker"
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null; then COMPOSE=(docker-compose)
else fail "Docker Compose non trovato."
fi
ok "Docker pronto ($(docker --version | sed 's/Docker version //; s/,.*//'))"
if [ -d "$CASAOS_APPS" ] || systemctl list-units --type=service 2>/dev/null | grep -q casaos; then
  CASAOS=1
  ok "CasaOS trovato: Sonora comparirà tra le app della dashboard"
else
  CASAOS=0
  warn "CasaOS non trovato: installo Sonora come normale container Docker"
fi
ARCH=$(uname -m)
case "$ARCH" in
  x86_64|aarch64|arm64) ok "Processore $ARCH supportato" ;;
  armv7l) warn "Processore a 32 bit ($ARCH): la build potrebbe richiedere parecchi minuti" ;;
  *) warn "Processore $ARCH non testato: provo comunque" ;;
esac

# --- configurazione esistente ------------------------------------------------------------------

if [ "$CASAOS" = 1 ]; then APP_DIR="$CASAOS_APPS/$APP"; else APP_DIR="/opt/$APP"; fi
ENV_FILE="$APP_DIR/.env"
UPDATE=0
if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  set -a; . "$ENV_FILE"; set +a
  PREV_PORT="${PORT:-}"
  step "Sonora è già installata"
  info "Dati in: ${DATA_DIR:-?}"
  if ask_yes "Aggiorno all'ultima versione mantenendo dati e impostazioni?" s; then
    UPDATE=1
  else
    info "Rifacciamo la configurazione (i dati restano dove sono)."
  fi
fi

# --- GitHub (pubblico o privato con token) -----------------------------------------------------

TOKEN=""
[ -r "$TOKEN_FILE" ] && TOKEN="$(cat "$TOKEN_FILE")"
gh_get() { # gh_get URL [header...]: GET su GitHub, con il token se c'è
  local url="$1"; shift
  if [ -n "$TOKEN" ]; then curl -fsSL --max-time 300 -H "Authorization: Bearer $TOKEN" "$@" "$url"
  else curl -fsSL --max-time 300 "$@" "$url"; fi
}

step "Scarico Sonora"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || echo "")"
if [ -n "$SCRIPT_DIR" ] && [ -f "$SCRIPT_DIR/../Dockerfile" ] && [ -d "$SCRIPT_DIR/../web" ]; then
  SRC="$(cd "$SCRIPT_DIR/.." && pwd)"
  ok "Uso il codice di questa cartella: $SRC"
else
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
  until gh_get "https://api.github.com/repos/$REPO/tarball/$BRANCH" | tar -xz -C "$WORK" 2>/dev/null; do
    warn "Non riesco a scaricare github.com/$REPO."
    info "Se il repository è privato serve un token GitHub con permesso di sola lettura"
    info "(github.com → Settings → Developer settings → Fine-grained tokens → Contents: Read-only)."
    printf "    Token GitHub (Invio per annullare): " >"$TTY"
    IFS= read -rs TOKEN <"$TTY" || true
    printf "\n" >"$TTY"
    [ -n "$TOKEN" ] || fail "Download non riuscito. Controlla la connessione o il token."
    rm -rf "${WORK:?}"/*
  done
  SRC="$(dirname "$(find "$WORK" -maxdepth 2 -name Dockerfile | head -n1)")"
  [ -f "$SRC/Dockerfile" ] || fail "Dockerfile non trovato nel download."
  ok "Scaricato da github.com/$REPO ($BRANCH)"
fi
if [ -z "$TOKEN" ] && ! curl -fsS -o /dev/null --max-time 15 "https://api.github.com/repos/$REPO"; then
  warn "Il repository github.com/$REPO è privato (o GitHub non risponde)."
  info "Aggiornamenti e download dell'app Android hanno bisogno di un token GitHub di sola lettura"
  info "(github.com → Settings → Developer settings → Fine-grained tokens → Contents: Read-only)."
  printf "    Token GitHub (Invio per saltare): " >"$TTY"
  IFS= read -rs TOKEN <"$TTY" || true
  printf "\n" >"$TTY"
  [ -n "$TOKEN" ] || warn "Senza token Sonora funziona, ma non si aggiorna e non scarica l'APK finché il repository è privato."
fi
if [ -n "$TOKEN" ]; then
  mkdir -p "$(dirname "$TOKEN_FILE")"
  (umask 077; printf "%s" "$TOKEN" > "$TOKEN_FILE")
  ok "Token salvato in $TOKEN_FILE (lo usano gli aggiornamenti)"
fi

# --- Tailscale ---------------------------------------------------------------------------------

# Tailscale può essere installato sul sistema o come app di CasaOS (un container).
TS=()
find_tailscale() {
  if command -v tailscale >/dev/null 2>&1 && tailscale status >/dev/null 2>&1; then TS=(tailscale); return 0; fi
  local name
  name=$(docker ps --format '{{.Names}} {{.Image}}' | awk 'tolower($2) ~ /tailscale/ {print $1; exit}')
  if [ -n "$name" ] && docker exec "$name" tailscale status >/dev/null 2>&1; then TS=(docker exec "$name" tailscale); return 0; fi
  return 1
}

# Le app già collegate a Tailscale sul server: una riga "porta destinazione" per ciascuna.
ts_served() {
  "${TS[@]}" serve status 2>/dev/null | awk '
    /^https:\/\// { n = split($1, a, ":"); port = (n > 2) ? a[3] : 443; sub(/\/.*/, "", port); next }
    index($0, "|--") == 1 && port != "" { print port, $NF; port = "" }'
}

# Porta HTTPS di Tailscale per Sonora: quella che la serve già, poi quella salvata se libera, poi la prima libera.
ts_port_for() {
  local saved="$1" served port target own
  served=$(ts_served)
  while read -r port target; do
    for own in $OWN_TARGETS; do
      [ -n "$port" ] && [ "$target" = "$own" ] && { echo "$port"; return; }
    done
  done <<< "$served"
  for port in $saved 443 8443 10000 10001 10002 10003 10004 10005 10006 10007 10008 10009; do
    echo "$served" | awk '{print $1}' | grep -qx "$port" || { echo "$port"; return; }
  done
}

LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
LAN_IP=${LAN_IP:-127.0.0.1}
port_free() { ! (ss -ltnH 2>/dev/null || netstat -ltn 2>/dev/null) | awk '{print $4}' | grep -Eq "[:.]$1\$"; }

# --- domande -----------------------------------------------------------------------------------

if [ "$UPDATE" = 0 ]; then
  step "Configurazione"
  default_data="/DATA/AppData/$APP"
  [ -d /DATA ] || default_data="/opt/$APP/data"
  ask "Cartella dove salvare profili, librerie e playlist" "${DATA_DIR:-$default_data}"
  DATA_DIR="$REPLY"

  while true; do
    ask "Porta per aprire Sonora nel browser" "${PORT:-$DEFAULT_PORT}"
    PORT="$REPLY"
    [[ "$PORT" =~ ^[0-9]+$ ]] && [ "$PORT" -ge 1 ] && [ "$PORT" -le 65535 ] || { warn "Scrivi un numero tra 1 e 65535."; continue; }
    docker ps --format '{{.Names}}' | grep -qx "$APP" && break
    port_free "$PORT" && break
    warn "La porta $PORT è già usata da un altro servizio, scegline un'altra."
  done

  host_tz=$(cat /etc/timezone 2>/dev/null || timedatectl show -p Timezone --value 2>/dev/null || echo "")
  ask "Fuso orario" "${TZ:-${host_tz:-Europe/Rome}}"
  TZ="$REPLY"

  step "Accesso da fuori casa (Tailscale)"
  info "Con Tailscale apri Sonora da telefono e PC ovunque tu sia, con HTTPS vero,"
  info "e solo i tuoi dispositivi possono raggiungerla."
  MODE=lan
  if find_tailscale; then
    ok "Tailscale è attivo su questo server"
    ask_yes "Uso Tailscale per Sonora?" s && MODE=tailscale
  else
    warn "Tailscale non è installato (o non è collegato) su questo server."
    if ask_yes "Lo installo adesso? Ti darò un link per accedere al tuo account Tailscale" s; then
      if ! command -v tailscale >/dev/null 2>&1; then
        curl -fsSL https://tailscale.com/install.sh | sh 2>&1 | sed 's/^/    /' || warn "Installazione di Tailscale non riuscita."
      fi
      if command -v tailscale >/dev/null 2>&1; then
        info "Apri il link che compare qui sotto e accedi: lo script continua da solo."
        tailscale up 2>&1 | sed 's/^/    /' || true
      fi
      if find_tailscale; then ok "Tailscale collegato"; MODE=tailscale
      else warn "Tailscale non è pronto: per ora Sonora funziona in rete locale. Rilancia lo script quando è attivo."
      fi
    else
      info "Va bene: Sonora funzionerà in rete locale. Puoi aggiungere Tailscale in seguito rilanciando lo script."
    fi
  fi
fi
MODE="${MODE:-lan}"

# --- build -------------------------------------------------------------------------------------

step "Preparo Sonora (la prima volta su un Raspberry servono 5-10 minuti)"
BUILD_LOG="$(mktemp)"
YTDLP_WEEK="$(date +%G%V)"
docker build --pull --build-arg "YTDLP_REFRESH=$YTDLP_WEEK" -t "$IMAGE" "$SRC" >"$BUILD_LOG" 2>&1 &
build_pid=$!
printf "    "
while kill -0 "$build_pid" 2>/dev/null; do printf "."; sleep 5; done
printf "\n"
if ! wait "$build_pid"; then
  tail -n 25 "$BUILD_LOG" | sed 's/^/    /'
  fail "Build non riuscita (log completo: $BUILD_LOG)"
fi
rm -f "$BUILD_LOG"
ok "Immagine $IMAGE pronta"

# --- avvio -------------------------------------------------------------------------------------

step "Avvio Sonora"
mkdir -p "$APP_DIR" "$DATA_DIR"
umask 077
cat > "$ENV_FILE" <<EOF
# Configurazione di Sonora, scritta da installa.sh
DATA_DIR=$DATA_DIR
PORT=$PORT
MODE=$MODE
TZ=$TZ
GITHUB_TOKEN=$TOKEN
YTDLP_WEEK=$YTDLP_WEEK
EOF
umask 022

cat > "$APP_DIR/docker-compose.yml" <<EOF
name: $APP
services:
  $APP:
    image: $IMAGE
    container_name: $APP
    pull_policy: never
    restart: unless-stopped
    ports:
      - "\${PORT}:8080"
    volumes:
      - type: bind
        source: \${DATA_DIR}
        target: /data
    environment:
      TZ: \${TZ}
      GITHUB_TOKEN: \${GITHUB_TOKEN}
      GITHUB_REPO: "$REPO"
    x-casaos:
      ports:
        - container: "8080"
          description:
            en_us: "Web app and API"
            it_it: "App web e API"
      volumes:
        - container: /data
          description:
            en_us: "Profiles, libraries, playlists and the Android app"
            it_it: "Profili, librerie, playlist e app Android"
x-casaos:
  architectures:
    - amd64
    - arm64
  main: $APP
  author: "carellice"
  developer: "carellice"
  category: "Media"
  icon: "http://$LAN_IP:$PORT/icon-512.png"
  title:
    en_us: "Sonora"
    it_it: "Sonora"
  tagline:
    en_us: "Your music, streamed from your home server"
    it_it: "La tua musica, in streaming dal server di casa"
  description:
    en_us: "Search YouTube Music, keep a library and playlists for each profile and listen on your phone or browser."
    it_it: "Cerca su YouTube Music, tieni libreria e playlist per ogni profilo e ascolta da telefono o browser."
  port_map: "$PORT"
  scheme: http
  index: /
EOF

(cd "$APP_DIR" && "${COMPOSE[@]}" --env-file "$ENV_FILE" up -d --force-recreate --remove-orphans) 2>&1 | sed 's/^/    /'
[ "$CASAOS" = 1 ] && systemctl restart casaos-app-management.service >/dev/null 2>&1 || true

printf "    Attendo che Sonora risponda"
for _ in $(seq 1 60); do
  if curl -fs "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then printf "\n"; ok "Sonora è in funzione"; break; fi
  printf "."
  sleep 2
done
curl -fs "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1 || { printf "\n"; fail "Sonora non risponde. Guarda i log con: docker logs $APP"; }

# --- Tailscale serve ---------------------------------------------------------------------------

URL_PHONE="http://$LAN_IP:$PORT"
TARGET="http://127.0.0.1:$PORT"
OWN_TARGETS="$TARGET"
[ -n "${PREV_PORT:-}" ] && OWN_TARGETS="$OWN_TARGETS http://127.0.0.1:$PREV_PORT"
sed -i '/^TS_HTTPS_PORT=/d' "$ENV_FILE"
if [ "$MODE" = tailscale ]; then
  step "Collego Tailscale"
  find_tailscale || true
  TS_PORT=""
  [ ${#TS[@]} -gt 0 ] && TS_PORT=$(ts_port_for "${TS_HTTPS_PORT:-}")
  if [ -n "$TS_PORT" ] && "${TS[@]}" serve --bg --https="$TS_PORT" "$TARGET" >/dev/null 2>&1; then
    echo "TS_HTTPS_PORT=$TS_PORT" >> "$ENV_FILE"
    TS_NAME=$("${TS[@]}" status --json 2>/dev/null | grep -o '"DNSName": *"[^"]*"' | head -n1 | sed 's/.*"\([^"]*\)"$/\1/; s/\.$//')
    if [ -n "$TS_NAME" ]; then
      URL_PHONE="https://$TS_NAME"
      [ "$TS_PORT" = 443 ] || URL_PHONE="$URL_PHONE:$TS_PORT"
      ok "Sonora è su $URL_PHONE (solo per i dispositivi del tuo Tailscale)"
    else
      warn "Collegato sulla porta $TS_PORT, ma non trovo il nome del server: guarda con  ${TS[*]} serve status"
    fi
  elif [ ${#TS[@]} -gt 0 ] && [ -z "$TS_PORT" ]; then
    warn "Tutte le porte HTTPS di Tailscale previste sono già usate da altre app."
    info "  Liberane una con  ${TS[*]} serve --https=<porta> off  e rilancia questo script."
  else
    warn "tailscale serve non riuscito. Abilita HTTPS nel pannello Tailscale (DNS → HTTPS Certificates) e rilancia:"
    info "  tailscale serve --bg --https=${TS_PORT:-443} $TARGET"
  fi
elif find_tailscale; then
  # Non più su Tailscale: toglie il collegamento rimasto da un'installazione precedente.
  ts_served | while read -r port target; do
    for own in $OWN_TARGETS; do
      [ "$target" = "$own" ] && "${TS[@]}" serve --https="$port" off >/dev/null 2>&1 || true
    done
  done
fi

# --- aggiornamenti -----------------------------------------------------------------------------

step "Aggiornamenti"
install -m 755 "$SRC/casaos/aggiorna.sh" /usr/local/bin/sonora-aggiorna
ok "Comando installato: sudo sonora-aggiorna"
AUTO_UPDATE=0
if command -v systemctl >/dev/null 2>&1 && [ -d /etc/systemd/system ]; then
  if systemctl is-enabled sonora-aggiorna.timer >/dev/null 2>&1; then
    AUTO_UPDATE=1
    ok "Aggiornamenti automatici già attivi"
  elif ask_yes "Controllo ogni notte se c'è una versione nuova e la installo da solo?" s; then
    cat > /etc/systemd/system/sonora-aggiorna.service <<'EOF'
[Unit]
Description=Aggiorna Sonora all'ultima versione
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/sonora-aggiorna
TimeoutStartSec=1h
Nice=10
EOF
    cat > /etc/systemd/system/sonora-aggiorna.timer <<'EOF'
[Unit]
Description=Controlla ogni notte se c'è una nuova versione di Sonora

[Timer]
OnCalendar=*-*-* 04:00
RandomizedDelaySec=30min
Persistent=true

[Install]
WantedBy=timers.target
EOF
    systemctl daemon-reload
    systemctl enable --now sonora-aggiorna.timer >/dev/null 2>&1 && AUTO_UPDATE=1
    [ "$AUTO_UPDATE" = 1 ] && ok "Ogni notte verso le 4 Sonora si aggiorna da sola" || warn "Timer non attivato: aggiorna a mano con sudo sonora-aggiorna"
  else
    info "Va bene: per aggiornare lancia  sudo sonora-aggiorna"
  fi
else
  warn "systemd non trovato: aggiorna a mano con sudo sonora-aggiorna"
fi

# --- fine --------------------------------------------------------------------------------------

step "Fatto!"
echo
info "${B}Apri Sonora:${R}"
info "  dal computer di casa:  http://$LAN_IP:$PORT"
[ "$MODE" = tailscale ] && info "  ovunque (Tailscale):   ${B}$URL_PHONE${R}"
echo
info "${B}App Android:${R} scaricala da  $URL_PHONE/sonora.apk"
info "  (oppure dalla web app: Impostazioni → Scarica l'app Android)."
info "  Al primo avvio l'app chiede l'indirizzo del server: scrivi  $URL_PHONE"
info "  ${D}L'APK arriva dalle release su GitHub: se non è ancora disponibile, riprova tra qualche minuto.${R}"
echo
info "${B}Installala come app dal browser:${R} Chrome ⋮ → Installa app · Safari Condividi → Aggiungi alla schermata Home"
info "Al primo accesso crea il tuo profilo: ogni persona può averne uno con libreria e playlist separate."
echo
if [ "$AUTO_UPDATE" = 1 ]; then
  info "Sonora si aggiorna da sola ogni notte. Per farlo subito: sudo sonora-aggiorna"
  info "${D}Ultimi aggiornamenti: journalctl -u sonora-aggiorna · Per spegnerli: systemctl disable --now sonora-aggiorna.timer${R}"
else
  info "Per aggiornare: sudo sonora-aggiorna"
fi
info "${D}Configurazione: $APP_DIR · Dati: $DATA_DIR · Log: docker logs $APP${R}"
echo
