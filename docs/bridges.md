# Matrix Talk bridges

Matrix bridges are server-side services. The Android application cannot connect
Telegram, WhatsApp, or Signal directly to `matrix.org`; a homeserver and bridge
instances must be operated by you or your provider.

## Included services

`docker-compose.bridges.yml` contains starter services for:

- Synapse;
- PostgreSQL;
- `mautrix-telegram`;
- `mautrix-whatsapp`;
- `mautrix-signal`.

The repository does not build a custom Docker image. Compose pulls the upstream
images listed in the file. Those services can be forced to run as ARM64 with
the `docker-compose.arm64.yml` override.

Bridge containers are placed in the `bridges` Compose profile so the homeserver
can be initialized before the bridge configuration is created.

## First setup

1. Install Docker Engine and Docker Compose.
2. Copy `.env.example` to `.env` and set a real server name and a long random
   PostgreSQL password:

   ```bash
   cp .env.example .env
   ```

On an ARM64 host, add the override file to each Compose command:

```bash
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml up -d synapse postgres
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml --profile bridges up -d
```

The override requires ARM64-compatible versions of the upstream images. Docker
may use emulation only when the host has configured it; it is not enabled by
this project.

3. Generate the Synapse configuration. Replace `example.org` with the DNS name
   that will be used by your Matrix server:

   ```bash
   mkdir -p server-data/synapse
   docker run --rm -it \
     -v "$PWD/server-data/synapse:/data" \
     -e SYNAPSE_SERVER_NAME=example.org \
     -e SYNAPSE_REPORT_STATS=no \
     matrixdotorg/synapse:latest generate
   ```

4. Configure Synapse to use PostgreSQL and expose the bridge appservices. Each
   bridge must have its own generated config and registration file. Follow the
   current setup instructions from the corresponding mautrix project before
   starting it.
5. Start Synapse and PostgreSQL:

   ```bash
   docker compose -f docker-compose.bridges.yml up -d synapse postgres
   ```

6. After configuring the bridges, start all bridge services:

   ```bash
   docker compose -f docker-compose.bridges.yml --profile bridges up -d
   ```

Do not commit `.env`, access tokens, bridge databases, generated registration
files, or encryption keys. The included Compose file is an operational
starting point, not a production security configuration. Use HTTPS, firewall
rules, backups, restricted bridge permissions, and pinned image versions before
exposing the server to the Internet.

## In Matrix Talk

Open the link icon in the Chats screen to see Telegram, WhatsApp, and Signal
bridge instructions. The app does not display a false “connected” state: actual
connection status is determined by the bridge and homeserver.
