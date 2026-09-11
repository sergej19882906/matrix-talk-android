# Matrix Talk for Android

Android-клиент Matrix Talk для децентрализованной сети Matrix, написанный на Kotlin с использованием Jetpack Compose.

## Возможности

- ✅ Вход в аккаунт Matrix по логину и паролю
- ✅ Вход по Matrix access token
- ✅ Список чатов/комнат
- ✅ Отправка текстовых сообщений
- ✅ Отправка изображений, видео, аудио и файлов
- ✅ Индикатор набора текста
- ✅ Реакции на сообщения
- ✅ Редактирование сообщений
- ✅ Удаление сообщений
- ✅ Изменение display name и аватара
- ✅ Прямые чаты (DM)
- ✅ Групповые комнаты
- ✅ Поддержка нескольких серверов
- ✅ Material Design 3
- ✅ Тёмная тема

## Технологии

- **Язык**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Архитектура**: MVVM + Clean Architecture
- **DI**: Hilt
- **Matrix SDK**: matrix-android-sdk2 1.6.62
- **Навигация**: Navigation Compose
- **Корутины**: kotlinx.coroutines
- **Flow**: StateFlow, Channel

## Структура проекта

```
app/
├── src/main/
│   ├── java/com/matrix/messenger/
│   │   ├── data/
│   │   │   ├── model/          # Модели данных
│   │   │   └── repository/     # Репозитории
│   │   ├── di/                 # Dependency Injection
│   
│   │   └── ui/
│   │       ├── chat/           # Экран чата
│   │       ├── home/           # Список чатов
│   │       ├── login/          # Экран входа
│   │       ├── navigation/     # Навигация
│   │       └── theme/          # Тема
│   └── res/                    # Ресурсы Android
```

## Сборка и запуск

### Требования

- Android Studio Ladybug (2024.2.1) или новее
- JDK 17
- Android SDK 35
- Эмулятор или устройство с Android 7.0+ (API 24)

### Шаги

1. Откройте проект в Android Studio
2. Дождитесь синхронизации Gradle
3. Запустите на эмуляторе или устройстве

```powershell
# Windows: сборка debug-версии
.\gradlew.bat assembleDebug

# Windows: установка на подключённое устройство
.\gradlew.bat installDebug

# Запуск тестов
.\gradlew.bat test

# Сборка release APK
.\gradlew.bat assembleRelease
```

Для Linux/macOS:

```bash
# Сделать скрипты исполняемыми (обычно достаточно выполнить один раз)
chmod +x gradlew build.sh

# Полная проверка и сборка debug-версии
./build.sh

# Отдельные Gradle-команды
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

`local.properties` создаётся локально и намеренно не добавляется в Git.

Release APK создаётся по адресу
`app/build/outputs/apk/release/app-release-unsigned.apk`. Это unsigned APK для
тестирования. Для публикации в Google Play его необходимо подписать собственным
keystore; ключи и пароли нельзя добавлять в репозиторий.

## Настройка

### Домашний сервер

По умолчанию используется `matrix.org`. Вы можете изменить его на экране входа.

Популярные серверы:
- `matrix.org` - публичный сервер
- `mozilla.org` - сервер Mozilla
- `gnome.org` - сервер GNOME

## Безопасность

- E2EE (End-to-End Encryption) поддерживается через Matrix SDK
- Не добавляйте в репозиторий access token, keystore или другие секреты.
- Access token вводится локально и не хранится в исходном коде.
- Поддержка Cross-Signing зависит от возможностей Matrix SDK и сервера.

## Статус проекта

Проект находится в активной разработке. Основной сценарий входа, просмотра комнат
и обмена сообщениями реализован. Push-уведомления, регистрация пользователя и
часть настроек безопасности требуют дальнейшей работы.

## GitHub Actions

Workflow [android.yml](.github/workflows/android.yml) запускает тесты, собирает
Debug и Release APK на каждый push и pull request, а Release APK публикуется как
GitHub Actions artifact.

## Лицензия

Apache License 2.0

## Ссылки

- [Matrix.org](https://matrix.org)
- [Matrix Android SDK](https://github.com/element-hq/matrix-android)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io)
