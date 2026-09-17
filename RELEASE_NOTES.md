# Matrix Talk — История версий

## 1.1.1
**Дата релиза:** 2026-09-18

### Что нового
- 🆕 **Аудио-видео звонки** — полноценная поддержка VoIP через WebRTC:
  - Исходящие и входящие аудио- и видеозвонки
  - Экран входящего звонка поверх заблокированного экрана (как в WhatsApp/Telegram)
  - Управление микрофоном (mute/unmute), камерой (вкл/выкл), переключение фронтальной/задней камеры
  - Foreground Service для поддержания звонка в фоне
  - Уведомления с кнопками «Принять», «Отклонить», «Завершить»
  - Таймер длительности звонка
  - Анимированный UI с пульсацией аватара при вызове
- 🆕 **Улучшенная передача файлов**:
  - Предпросмотр видео в чате (Coil Video)
  - Прогресс-бар загрузки/скачивания файлов
  - Поддержка Drag-and-Drop для файлов
- 🆕 **Новые компоненты**:
  - `CallActivity` и `IncomingCallActivity` — полноэкранные Activity для звонков
  - `CallService` — Foreground Service для Android 14+ (API 34)
  - `CallScreen` — Compose UI экрана звонка
  - `CallViewModel` и `CallRepository` — логика и интеграция с Matrix SDK VoIP
  - `CallState` — модель состояний звонка (Idle, Incoming, Outgoing, Connected, Ended)
- 🆕 **Обновлённая навигация**:
  - Кнопки аудио- и видеозвонка в AppBar чата
  - Новый маршрут `Screen.Call` в NavGraph
  - URL-кодирование параметров навигации
- 🆕 **Новые зависимости**:
  - `io.getstream:stream-webrtc-android:1.0.0` — WebRTC
  - `com.google.accompanist:accompanist-permissions:0.32.0` — запрос разрешений
  - `io.coil-kt:coil-video:2.7.0` — предпросмотр видео
- 🔧 Обновлён `AndroidManifest.xml`:
  - Добавлены разрешения: `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE_CAMERA/MICROPHONE/PHONE_CALL`, `BLUETOOTH_CONNECT`, `DISABLE_KEYGUARD`, `TURN_SCREEN_ON`
  - `tools:targetApi` обновлён до 34

### Сборка
- `versionCode`: 3
- `versionName`: 1.1.1
- Минимальная версия Android: API 24
- Целевая версия Android: API 35

### Проверка
- `./gradlew.bat assembleDebug` — успешно.

---

## 1.1
**Дата релиза:** 2026-09-12

### Что нового
- Добавлена настройка мостов Telegram, WhatsApp и Signal через Matrix homeserver.
- Добавлена поддержка ARM64-конфигурации Docker Compose для серверной заготовки мостов.
- Обновлён Matrix Android SDK до версии 1.6.62.
- Добавлены экраны и сценарии для входа, списка комнат, чатов и обмена сообщениями.
- Добавлена отправка изображений, видео, аудио и файлов.
- Добавлены реакции, редактирование и удаление сообщений.
- Добавлена поддержка прямых и групповых комнат, нескольких серверов и тёмной темы.
- Добавлены демонстрационные скриншоты интерфейса.

### Сборка
- `versionCode`: 2
- `versionName`: 1.1
- Минимальная версия Android: API 24
- Целевая версия Android: API 35

### Проверка
- `./gradlew.bat assembleDebug` — успешно.