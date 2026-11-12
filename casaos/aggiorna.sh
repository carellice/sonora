#!/usr/bin/env bash
# Aggiorna Sonora all'ultima versione pubblicata su GitHub. Non fa domande, quindi va bene anche per
# il timer notturno che installa.sh può attivare (sonora-aggiorna.timer).
#
#   sudo sonora-aggiorna              aggiorna se c'è una versione nuova (e una volta a settimana yt-dlp)
#   sudo sonora-aggiorna --controlla  dice soltanto se c'è una versione nuova
#   sudo sonora-aggiorna --forza      ricostruisce Sonora anche se la versione è la stessa
#   sudo sonora-aggiorna --token      imposta (o cambia) il token GitHub, poi aggiorna
#
# La versione è quella di server/package.json: cambiarla su GitHub è il rilascio (server, web app e APK).
# Prima di aggiornare salva una copia dei dati; se la nuova versione non parte, torna a quella di prima.
# L'APK Android lo scarica il server stesso dalle release di GitHub.

set -euo pipefail

REPO="carellice/sonora"
BRANCH="${SONORA_BRANCH:-main}"
APP="sonora"
IMAGE="sonora:latest"
NEW_IMAGE="sonora:nuova"
OLD_IMAGE="sonora:precedente"
TOKEN_FILE="/etc/sonora/github-token"
KEEP_BACKUPS=5

if [ -t 1 ]; then
  B=$'\e[1m'; R=$'\e[0m'; V=$'\e[35m'; G=$'\e[32m'; Y=$'\e[33m'; E=$'\e[31m'
else
  B=""; R=""; V=""; G=""; Y=""; E=""
fi
step() { printf "${V}${B}==> %s${R}\n" "$1"; }
info() { printf "    %s\n" "$1"; }
ok() { printf "    ${G}✓${R} %s\n" "$1"; }
warn() { printf "    ${Y}!${R} %s\n" "$1"; }
fail() { printf "${E}${B}ERRORE:${R} %s\n" "$1" >&2; exit 1; }

json_version() { grep -o '"version": *"[^"]*"' | head -n1 | sed 's/.*"\([^"]*\)"$/\1/'; }
running_version() { curl -fs --max-time 5 "http://127.0.0.1:$PORT/api/health" 2>/dev/null | json_version || true; }
gh_get() { # gh_get URL [header...]: GET su GitHub, con il token se il repository è privato
  local url="$1"; shift
  if [ -n "$TOKEN" ]; then curl -fsSL --max-time 300 -H "Authorization: Bearer $TOKEN" "$@" "$url"
  else curl -fsSL --max-time 300 "$@" "$url"; fi
}
recreate() { (cd "$APP_DIR" && "${COMPOSE[@]}" --env-file "$ENV_FILE" up -d --force-recreate --remove-orphans) >/dev/null 2>&1; }
wait_for() { # wait_for versione: vero quando Sonora risponde con quella versione
  for _ in $(seq 1 60); do
    [ "$(running_version)" = "$1" ] && return 0
    sleep 2
  done
  return 1
}

# Tutto dentro main: bash legge lo script per intero prima di eseguirlo, così può sostituire
# se stesso in /usr/local/bin mentre gira.
main() {
  local mode=update
  case "${1:-}" in
    "") ;;
    --controlla|--check) mode=check ;;
    --forza|--force) mode=force ;;
    --token) mode=token ;;
    *) fail "Opzione sconosciuta: $1 (usa --controlla, --forza oppure --token)" ;;
  esac

  [ "$(id -u)" -eq 0 ] || fail "Lancia come amministratore: sudo sonora-aggiorna"
  command -v docker >/dev/null || fail "Docker non trovato."
  command -v curl >/dev/null || fail "Serve curl."
  if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null; then COMPOSE=(docker-compose)
  else fail "Docker Compose non trovato."
  fi

  APP_DIR=""
  for dir in "/var/lib/casaos/apps/$APP" "/opt/$APP"; do
    [ -f "$dir/.env" ] && { APP_DIR="$dir"; break; }
  done
  [ -n "$APP_DIR" ] || fail "Sonora non è installata qui: usa prima installa.sh"
  ENV_FILE="$APP_DIR/.env"
  # shellcheck disable=SC1090
  set -a; . "$ENV_FILE"; set +a
  TOKEN=""
  [ -r "$TOKEN_FILE" ] && TOKEN="$(cat "$TOKEN_FILE")"

  # Token per il repository privato: serve agli aggiornamenti (qui) e al server per scaricare l'APK (.env).
  if [ "$mode" = token ]; then
    [ -r /dev/tty ] || fail "Serve un terminale per inserire il token."
    printf "    Token GitHub di sola lettura (Invio per toglierlo): " >/dev/tty
    IFS= read -rs TOKEN </dev/tty || true
    printf "
" >/dev/tty
    mkdir -p "$(dirname "$TOKEN_FILE")"
    if [ -n "$TOKEN" ]; then (umask 077; printf "%s" "$TOKEN" > "$TOKEN_FILE"); else rm -f "$TOKEN_FILE"; fi
    sed -i '/^GITHUB_TOKEN=/d' "$ENV_FILE"
    echo "GITHUB_TOKEN=$TOKEN" >> "$ENV_FILE"
    recreate || true
    ok "Token salvato"
    mode=update
  fi

  # Un aggiornamento alla volta, per esempio il timer e un lancio a mano.
  if command -v flock >/dev/null; then
    exec 9>/run/sonora-aggiorna.lock
    flock -n 9 || fail "C'è già un aggiornamento in corso."
  fi

  local installed latest week
  installed=$(running_version)
  latest=$(gh_get "https://api.github.com/repos/$REPO/contents/server/package.json?ref=$BRANCH" -H "Accept: application/vnd.github.raw" | json_version) \
    || fail "Non riesco a leggere l'ultima versione da GitHub (connessione o token)."
  [ -n "$latest" ] || fail "Versione su GitHub non trovata."
  info "Installata: ${installed:-Sonora non risponde} · ultima: $latest"

  # yt-dlp deve stare al passo con YouTube: una volta a settimana ricostruisco anche senza versione nuova.
  week="$(date +%G%V)"
  if [ "$installed" = "$latest" ] && [ "$mode" = update ]; then
    if [ "${YTDLP_WEEK:-}" = "$week" ]; then ok "Sonora è già aggiornata"; return 0; fi
    info "Stessa versione: aggiorno solo yt-dlp (una volta a settimana)."
  fi
  if [ "$mode" = check ]; then
    [ "$installed" = "$latest" ] && ok "Sonora è già aggiornata" || info "C'è una versione nuova: aggiorna con  sudo sonora-aggiorna"
    return 0
  fi

  step "Scarico Sonora $latest"
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
  gh_get "https://api.github.com/repos/$REPO/tarball/$BRANCH" | tar -xz -C "$WORK" || fail "Download da GitHub non riuscito."
  local src version
  src="$(dirname "$(find "$WORK" -maxdepth 2 -name Dockerfile | head -n1)")"
  [ -f "$src/Dockerfile" ] || fail "Dockerfile non trovato nel download."
  version=$(json_version <"$src/server/package.json")
  ok "Scaricato"

  # La nuova immagine ha un nome a parte: se la build non riesce, Sonora resta com'è.
  step "Preparo la nuova versione (su un Raspberry servono alcuni minuti)"
  local log="$WORK/build.log"
  if ! docker build --pull --build-arg "YTDLP_REFRESH=$week" -t "$NEW_IMAGE" "$src" >"$log" 2>&1; then
    tail -n 25 "$log" | sed 's/^/    /'
    fail "Build non riuscita: Sonora resta alla versione ${installed:-di prima}."
  fi
  ok "Immagine pronta"

  step "Salvo una copia dei dati"
  local name
  name="${installed:-sconosciuta}-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$DATA_DIR/backup/$name"
  if cp "$DATA_DIR"/*.json "$DATA_DIR/backup/$name/" 2>/dev/null; then
    ok "Copia in $DATA_DIR/backup/$name"
    ls -1dt "$DATA_DIR"/backup/*/ 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm -rf || true
  else
    rmdir "$DATA_DIR/backup/$name" 2>/dev/null || true
    info "Nessun dato da copiare."
  fi

  step "Avvio Sonora $version"
  local had_old=0
  if docker image inspect "$IMAGE" >/dev/null 2>&1; then
    docker tag "$IMAGE" "$OLD_IMAGE"
    had_old=1
  fi
  docker tag "$NEW_IMAGE" "$IMAGE"
  docker rmi "$NEW_IMAGE" >/dev/null 2>&1 || true
  recreate || true

  if ! wait_for "$version"; then
    if [ "$had_old" = 1 ]; then
      warn "La nuova versione non parte: torno a quella di prima."
      docker tag "$OLD_IMAGE" "$IMAGE"
      recreate || true
      docker rmi "$OLD_IMAGE" >/dev/null 2>&1 || true
    fi
    fail "Sonora $version non risponde. Log con: docker logs $APP"
  fi
  ok "Sonora $version è in funzione"
  sed -i '/^YTDLP_WEEK=/d' "$ENV_FILE"
  echo "YTDLP_WEEK=$week" >> "$ENV_FILE"

  # Pulizia: la vecchia immagine e la cache della build occupano spazio sulla scheda SD.
  docker rmi "$OLD_IMAGE" >/dev/null 2>&1 || true
  docker builder prune -f >/dev/null 2>&1 || true

  # Anche questo comando si aggiorna, per la prossima volta.
  if [ -f "$src/casaos/aggiorna.sh" ] && [ -d /usr/local/bin ]; then
    cp "$src/casaos/aggiorna.sh" /usr/local/bin/.sonora-aggiorna.new
    chmod 755 /usr/local/bin/.sonora-aggiorna.new
    mv -f /usr/local/bin/.sonora-aggiorna.new /usr/local/bin/sonora-aggiorna
  fi
  ok "Aggiornamento completato. Telefoni e browser prendono la nuova versione alla prossima apertura;"
  info "l'APK nuovo arriva sul server entro pochi minuti e l'app Android lo propone da sola."
}

main "$@"
