# Matrix Talk Server Setup

This guide covers deploying a Matrix homeserver with VoIP calling and optional
messenger bridges, matching the capabilities of the Matrix Talk Android app.

## Included services

`docker-compose.bridges.yml` defines:

| Service | Image | Purpose |
|---------|-------|---------|
| Synapse | `matrixdotorg/synapse:latest` | Matrix homeserver |
| PostgreSQL | `postgres:16-alpine` | Synapse database |
| Coturn | `coturn/coturn:latest` | TURN/STUN server for VoIP calls |
| mautrix-telegram | `dock.mau.dev/mautrix/telegram:latest` | Telegram bridge |
| mautrix-whatsapp | `dock.mau.dev/mautrix/whatsapp:latest` | WhatsApp bridge |
| mautrix-signal | `dock.mau.dev/mautrix/signal:latest` | Signal bridge |

Bridge containers use the `bridges` Compose profile so Synapse can start first.

## First setup

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and set:
- `MATRIX_SERVER_NAME` — your domain (e.g. `matrix.example.org`)
- `POSTGRES_PASSWORD` — long random password
- `TURN_SHARED_SECRET` — generate with `openssl rand -hex 32`
- `SYNAPSE_REGISTRATION_SHARED_SECRET` — generate with `openssl rand -hex 32`

### 2. Generate Synapse config

```bash
mkdir -p server-data/synapse
docker run --rm -it \
  -v "$PWD/server-data/synapse:/data" \
  -e SYNAPSE_SERVER_NAME=example.org \
  -e SYNAPSE_REPORT_STATS=no \
  matrixdotorg/synapse:latest generate
```

### 3. Configure Synapse to use PostgreSQL

Edit `server-data/synapse/homeserver.yaml`. Replace the default SQLite database
block with:

```yaml
database:
  name: psycopg2
  allow_unsafe_locale: true
  args:
    user: synapse
    password: YOUR_POSTGRES_PASSWORD
    database: synapse
    host: postgres
    cp_min: 5
    cp_max: 10
```

### 4. Enable VoIP calling (TURN)

Add the following to `server-data/synapse/homeserver.yaml`:

```yaml
turn_uris:
  - "turn:YOUR_DOMAIN?transport=udp"
  - "turn:YOUR_DOMAIN?transport=tcp"
turn_shared_secret: "YOUR_TURN_SHARED_SECRET"
turn_username_lifetime: 86400000
```

Replace `YOUR_DOMAIN` with your `MATRIX_SERVER_NAME` and `YOUR_TURN_SHARED_SECRET`
with the value from `.env`.

**Without a TURN server, VoIP calls only work on the same local network.**
Coturn enables audio/video calls across NAT and the internet.

### 5. Open firewall ports

| Port | Protocol | Service |
|------|----------|---------|
| 8008 | TCP | Synapse (Client & Federation API) |
| 3478 | UDP | Coturn TURN/STUN |
| 5349 | TCP | Coturn TURN over TLS (if certs configured) |
| 49152–65535 | UDP | Coturn media relay |

### 6. Create the admin user

Start Synapse and PostgreSQL first:

```bash
docker compose -f docker-compose.bridges.yml up -d synapse postgres coturn
```

Wait for Synapse to be ready, then register an admin account:

```bash
docker exec -it matrix-talk-synapse register_new_matrix_user \
  -c /data/homeserver.yaml \
  --admin \
  --password-prompt \
  @admin:example.org
```

### 7. Start bridge services (optional)

Each bridge needs its own generated config and a registration file added to
Synapse. Follow the current instructions from the mautrix project for each
bridge before starting it.

After bridge configuration:

```bash
docker compose -f docker-compose.bridges.yml --profile bridges up -d
```

## ARM64 hosts

Add the override file to every Compose command:

```bash
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml up -d synapse postgres coturn
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml --profile bridges up -d
```

## Production notes

- **HTTPS:** Use a reverse proxy (Caddy, nginx, Traefik) in front of Synapse.
  Caddy provides automatic TLS and is the simplest option.
- **Federation:** Add `/.well-known/matrix/server` and `/.well-known/matrix/client`
  DNS/HTTP records pointing to your Synapse.
- **Coturn TLS:** Place `cert.pem` and `key.pem` in `server-data/coturn/` and
  add `--cert=/etc/coturn/cert.pem --pkey=/etc/coturn/key.pem` to the command.
- **Backups:** Back up `server-data/postgres` and `server-data/synapse` regularly.
- **Pin versions:** Replace `latest` tags with specific versions for reproducibility.
- **Do not commit** `.env`, access tokens, bridge databases, registration files,
  or encryption keys.

## In Matrix Talk

Open the link icon in the Chats screen to see Telegram, WhatsApp, and Signal
bridge instructions. The app does not display a false "connected" state: actual
connection status is determined by the bridge and homeserver.

VoIP calls use WebRTC through the TURN server configured above. The app requests
TURN credentials from Synapse when initiating or receiving a call.
