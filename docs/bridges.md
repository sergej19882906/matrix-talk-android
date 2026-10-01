# Инструкция по установке и настройке сервера Matrix Talk

Полное руководство по развёртыванию собственного Matrix-сервера с поддержкой
мессенджера, аудио/видео звонков и опциональных мостов Telegram, WhatsApp и Signal.

---

## Что входит в сервер

`docker-compose.bridges.yml` определяет 6 сервисов:

| Сервис | Образ | Назначение |
|--------|-------|------------|
| **Synapse** | `matrixdotorg/synapse:latest` | Matrix homeserver — ядро мессенджера |
| **PostgreSQL** | `postgres:16-alpine` | База данных Synapse |
| **Coturn** | `coturn/coturn:latest` | TURN/STUN сервер для VoIP-звонков |
| **mautrix-telegram** | `dock.mau.dev/mautrix/telegram:latest` | Мост Telegram |
| **mautrix-whatsapp** | `dock.mau.dev/mautrix/whatsapp:latest` | Мост WhatsApp |
| **mautrix-signal** | `dock.mau.dev/mautrix/signal:latest` | Мост Signal |

Мосты используют профиль Compose `bridges`, чтобы Synapse запускался первым.

---

## Требования

- Docker Engine 20.10+ и Docker Compose v2+
- Доменное имя с DNS A-записью, указывающей на ваш сервер
- Открытые порты (см. раздел «Порты» ниже)

---

## Шаг 1. Настройка переменных окружения

```bash
cp .env.example .env
```

Отредактируйте `.env`:

```ini
# === Matrix Talk Server Configuration ===

# --- Homeserver ---
# Ваш домен (должен совпадать с DNS / обратным прокси)
MATRIX_SERVER_NAME=matrix.example.org

# --- PostgreSQL ---
POSTGRES_USER=synapse
POSTGRES_PASSWORD=<сгенерируйте длинный случайный пароль>
POSTGRES_DB=synapse

# --- TURN Server (VoIP звонки) ---
# Общий секрет для TURN-аутентификации
# Генерация: openssl rand -hex 32
TURN_SHARED_SECRET=<сгенерируйте случайный секрет>
# UDP-порт TURN/STUN
TURN_PORT=3478
# TLS-порт (требует сертификаты в server-data/coturn/)
TURN_TLS_PORT=5349
# Диапазон портов для медиа-релея
TURN_MIN_PORT=49152
TURN_MAX_PORT=65535

# --- Synapse Admin ---
# Секрет для скрипта register_new_matrix_user
# Генерация: openssl rand -hex 32
SYNAPSE_REGISTRATION_SHARED_SECRET=<сгенерируйте случайный секрет>
```

> **⚠️ Никогда не коммитьте `.env` в git.** Файл добавлен в `.gitignore`.

---

## Шаг 2. Генерация конфигурации Synapse

Новый Docker-образ Synapse **не генерирует** конфиг из переменных окружения
автоматически. Нужно запустить генерацию вручную:

```bash
mkdir -p server-data/synapse

docker run --rm -it \
  -v "$PWD/server-data/synapse:/data" \
  -e SYNAPSE_SERVER_NAME=matrix.example.org \
  -e SYNAPSE_REPORT_STATS=no \
  matrixdotorg/synapse:latest generate
```

Эта команда создаст:
- `server-data/synapse/homeserver.yaml` — основной конфиг
- `server-data/synapse/<домен>.signing.key` — ключ подписи сервера
- `server-data/synapse/<домен>.log.config` — конфиг логирования

> **На Windows (PowerShell):** замените `$PWD` на полный путь, например
> `E:\PR\matrix-messenger-android\server-data\synapse`.

---

## Шаг 3. Настройка PostgreSQL

Откройте `server-data/synapse/homeserver.yaml` и замените блок `database`
(по умолчанию SQLite) на PostgreSQL:

```yaml
database:
  name: psycopg2
  allow_unsafe_locale: true    # Обязательно! Docker postgres использует en_US.utf8
  args:
    user: synapse
    password: ВАШ_POSTGRES_PASSWORD   # из .env
    database: synapse
    host: postgres                    # имя контейнера Docker
    cp_min: 5
    cp_max: 10
```

> **⚠️ `allow_unsafe_locale` должен быть на уровне `database:`, а не внутри `args:`.**
> Docker-образ `postgres:16-alpine` использует collation `en_US.utf8`, а Synapse
> требует `C`. Без этого флага Synapse не запустится.

---

## Шаг 4. Настройка VoIP-звонков (TURN)

Без TURN-сервера VoIP-звонки работают **только в одной локальной сети**.
Coturn обеспечивает проброс NAT для аудио/видео звонков через интернет.

Добавьте в конец `server-data/synapse/homeserver.yaml`:

```yaml
turn_uris:
  - "turn:matrix.example.org?transport=udp"
  - "turn:matrix.example.org?transport=tcp"
turn_shared_secret: "ВАШ_TURN_SHARED_SECRET"   # из .env
turn_username_lifetime: 86400000               # 24 часа в мс
```

Замените `matrix.example.org` на ваш `MATRIX_SERVER_NAME`.

---

## Шаг 5. Порты фаервола

Откройте на сервере следующие порты:

| Порт | Протокол | Сервис | Назначение |
|------|----------|--------|------------|
| 8008 | TCP | Synapse | Client API + Federation API |
| 8448 | TCP | Synapse | Federation (если прямой TLS) |
| 3478 | UDP | Coturn | TURN/STUN |
| 5349 | TCP | Coturn | TURN over TLS (если есть сертификаты) |
| 49152–65535 | UDP | Coturn | Медиа-релей для VoIP |

---

## Шаг 6. Запуск сервера

### Основные сервисы (мессенджер + VoIP)

```bash
docker compose -f docker-compose.bridges.yml up -d synapse postgres coturn
```

Дождитесь запуска (Synapse станет `healthy`):

```bash
docker compose -f docker-compose.bridges.yml ps
```

### Проверка

```bash
curl http://localhost:8008/_matrix/client/versions
```

Должен вернуть JSON с версиями API (v1.1–v1.12).

---

## Шаг 7. Создание администратора

```bash
docker exec -it matrix-talk-synapse register_new_matrix_user \
  -c /data/homeserver.yaml \
  --admin \
  --password-prompt \
  @admin:matrix.example.org
```

Введите пароль при запросе. Этот аккаунт будет использоваться для входа в
приложение Matrix Talk.

---

## Шаг 8. Мосты Telegram / WhatsApp / Signal (опционально)

### 8.1. Создание баз данных для мостов

Мосты используют отдельные базы данных в PostgreSQL:

```bash
docker exec -it matrix-talk-postgres psql -U synapse -c \
  "CREATE DATABASE mautrix_telegram; CREATE DATABASE mautrix_whatsapp; CREATE DATABASE mautrix_signal;"
```

### 8.2. Генерация конфигурации мостов

Запустите каждый контейнер моста один раз для генерации `config.yaml`:

```bash
# Telegram (требует API ID/hash с https://my.telegram.org/apps)
docker run --rm -v "$PWD/server-data/mautrix-telegram:/data" \
  dock.mau.dev/mautrix/telegram:latest

# WhatsApp
docker run --rm -v "$PWD/server-data/mautrix-whatsapp:/data" \
  dock.mau.dev/mautrix/whatsapp:latest

# Signal
docker run --rm -v "$PWD/server-data/mautrix-signal:/data" \
  dock.mau.dev/mautrix/signal:latest
```

Запустите ещё раз после редактирования (см. ниже) для генерации `registration.yaml`.

### 8.3. Правка конфигурации каждого моста

Для **каждого** моста отредактируйте `server-data/mautrix-<bridge>/config.yaml`:

```yaml
# 1. Адрес homeserver (внутри Docker-сети)
homeserver:
    address: http://synapse:8008
    domain: matrix.example.org        # ваш MATRIX_SERVER_NAME

# 2. Слушать на всех интерфейсах (для Docker)
appservice:
    hostname: 0.0.0.0
    # порт: 29317 (telegram), 29318 (whatsapp), 29328 (signal)

# 3. База данных (отдельная для каждого моста)
database:
    type: postgres
    uri: postgres://synapse:ПАРОЛЬ@postgres/mautrix_ТЕГ_МОСТА?sslmode=disable
    #                                 ↑ имя БД из шага 8.1

# 4. Права доступа
bridge:
    permissions:
        "*": relay
        "matrix.example.org": user
        "@admin:matrix.example.org": admin
```

**Telegram дополнительно** — укажите реальные API ключи:
```yaml
network:
    api_id: ВАШ_API_ID       # с https://my.telegram.org/apps
    api_hash: ВАШ_API_HASH   # с https://my.telegram.org/apps
```

> **⚠️ Без `api_id`/`api_hash` Telegram-мост не запустится.**
> Получите их на https://my.telegram.org/apps (войдите → API development tools).

### 8.4. Генерация registration-файлов

Повторно запустите каждый контейнер — мост прочтёт отредактированный конфиг
и сгенерирует `registration.yaml` с `as_token`/`hs_token`:

```bash
docker run --rm -v "$PWD/server-data/mautrix-telegram:/data" \
  dock.mau.dev/mautrix/telegram:latest
# Повторите для whatsapp и signal
```

### 8.5. Регистрация мостов в Synapse

Добавьте в `server-data/synapse/homeserver.yaml`:

```yaml
app_service_config_files:
  - /data/mautrix-telegram/registration.yaml
  - /data/mautrix-whatsapp/registration.yaml
  - /data/mautrix-signal/registration.yaml
```

Файлы мостов маунтятся в контейнер Synapse через docker-compose (тома `:ro`).
Перезапустите Synapse:

```bash
docker compose -f docker-compose.bridges.yml up -d --force-recreate synapse
```

### 8.6. Запуск мостов

```bash
docker compose -f docker-compose.bridges.yml --profile bridges up -d
```

Проверка:

```bash
docker compose -f docker-compose.bridges.yml --profile bridges ps
```

Все контейнеры должны быть `Up` (не `Restarting`).

> **⚠️ Права файлов:** если мосты падают с `Permission denied`, выполните:
> ```bash
> docker run --rm -v "$PWD/server-data:/server-data" alpine \
>   chmod -R 777 /server-data/mautrix-telegram /server-data/mautrix-whatsapp /server-data/mautrix-signal
> ```

### 8.7. Привязка аккаунтов

В Matrix Talk откройте экран «Мосты» (иконка ссылки на экране чатов) и
следуйте инструкции:

- **Telegram:** отправьте `!tg login` в комнату моста, введите номер телефона
- **WhatsApp:** отправьте `!wa login` и отсканируйте QR-код
- **Signal:** отправьте `!signal login` и привяжите устройство

> Подробные инструкции: [mautrix-telegram](https://docs.mau.fi/bridges/go/telegram/),
> [mautrix-whatsapp](https://docs.mau.fi/bridges/go/whatsapp/),
> [mautrix-signal](https://docs.mau.fi/bridges/go/signal/)

---

## ARM64 (Raspberry Pi и др.)

Добавьте файл переопределения ко всем командам:

```bash
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml up -d synapse postgres coturn
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml --profile bridges up -d
```

---

## Продакшен

### HTTPS / обратный прокси

Рекомендуется **Caddy** — автоматический TLS:

```
matrix.example.org {
    reverse_proxy localhost:8008
}
```

Или **nginx**:

```nginx
server {
    listen 443 ssl;
    server_name matrix.example.org;

    ssl_certificate /etc/ssl/certs/matrix.example.org.crt;
    ssl_certificate_key /etc/ssl/private/matrix.example.org.key;

    location / {
        proxy_pass http://localhost:8008;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
```

### Федерация

Для федерации с другими серверами Matrix создайте `.well-known`:

`https://matrix.example.org/.well-known/matrix/server`:
```json
{"m.server": "matrix.example.org:443"}
```

`https://matrix.example.org/.well-known/matrix/client`:
```json
{"m.homeserver": {"base_url": "https://matrix.example.org"}}
```

### Coturn с TLS

Поместите `cert.pem` и `key.pem` в `server-data/coturn/` и добавьте в
`docker-compose.bridges.yml` параметры Coturn:

```yaml
command:
  - "--cert=/etc/coturn/cert.pem"
  - "--pkey=/etc/coturn/key.pem"
```

### Бэкапы

Регулярно бэкапьте:
- `server-data/postgres/` — база данных
- `server-data/synapse/` — конфигурация, ключи подписи, медиа

### Рекомендации

- **Пиньте версии** — замените `latest` на конкретные теги образов
- **Не коммитьте** `.env`, токены доступа, базы мостов, файлы регистрации,
  ключи шифрования
- **Ограничьте права** мостов минимально необходимыми
- **Настройте мониторинг** логов Synapse и Coturn

---

## Подключение из Matrix Talk

1. Откройте приложение
2. На экране входа укажите адрес homeserver: `https://matrix.example.org`
3. Введите логин и пароль администратора (шаг 7)
4. Для настройки мостов — нажмите иконку ссылки на экране чатов

VoIP-звонки используют WebRTC через настроенный TURN-сервер. Приложение
автоматически запрашивает TURN-учётные данные у Synapse при звонке.
