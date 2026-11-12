# Sonora: web app + API + APK distribution, in one container.

# --- web app (React + Vite) -> /src/server/public ---
FROM node:22-bookworm-slim AS web
WORKDIR /src/web
COPY web/package*.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN mkdir -p /src/server && npm run build

# --- runtime ---
FROM node:22-bookworm-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-pip ca-certificates tini \
 && rm -rf /var/lib/apt/lists/*
# yt-dlp follows YouTube's changes: sonora-aggiorna passes a new value every week to reinstall it.
ARG YTDLP_REFRESH=0
RUN echo "yt-dlp $YTDLP_REFRESH" \
 && pip3 install --no-cache-dir --break-system-packages "yt-dlp[default]" \
 && printf -- '--js-runtimes node\n' > /etc/yt-dlp.conf
WORKDIR /app
COPY server/package*.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY server/server.mjs ./
COPY --from=web /src/server/public ./public
ENV NODE_ENV=production DATA_DIR=/data PORT=8080 HOME=/data
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=1m --timeout=5s CMD node -e "fetch('http://127.0.0.1:8080/api/health').then(r=>process.exit(r.ok?0:1),()=>process.exit(1))"
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["node", "server.mjs"]
