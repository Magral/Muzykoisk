# Музыкоиск — Android prototype v0.1.2

Kotlin / Jetpack Compose, без собственного сервера и обязательных API-ключей.

## Реализовано
- Параллельный поиск с тайм-аутами и отменой.
- HTML-адаптеры для публичных главных страниц Muzbab, MUZYQA, Nishevo на Jsoup.
- Дополнительный поиск в Internet Archive.
- Переход на страницу трека.
- Android DownloadManager для файлов, на которые **явно подтверждено** разрешение скачивания.

## Важные ограничения
- HTML-адаптеры сейчас просматривают **только ссылки на главной странице**, а не весь каталог сайта. Это экспериментальная интеграция; конкретные треки могут не находиться.
- У HTML-результатов статус права UNKNOWN, поэтому прямая кнопка скачивания для них не показывается. Надпись «скачать бесплатно» не является доказательством лицензии.
- JamendoSource оставлен в коде, но не включён: требует client ID.
- Не проверена компиляция и работа на устройстве; APK не собран.
- Чарты, библиотека, проверка реального MP3 и полный поиск по сайтам пока не реализованы.

## Сборка
Android Studio, JDK 17, Android SDK 35. Gradle wrapper и CI пока отсутствуют.

## Следующий этап
1. Проверить HTML-разметку и маршруты поиска каждого сайта в реальных сетевых тестах.
2. Добавить отдельные парсеры поисковой выдачи с ограничением частоты запросов.
3. Подключить прямое скачивание только из источников с проверенными разрешениями.
4. Добавить Gradle wrapper и CI сборку APK.

## v0.1.3 (development)
- Added GitHub Actions workflow to build and upload a debug APK artifact on push to main or manual dispatch.
- Added ChartSource for public weekly chart links and a chart screen; site markup is not guaranteed and needs on-device verification.
- Added generic SiteSearchSource for confirmed search endpoints; not enabled until endpoint forms are verified.
- HTML homepage adapters remain experimental and do not constitute full-catalog search.
- Downloader code exists, but no source currently supplies a rights-verified MP3 URL. **No successful MP3 download has been confirmed.**
- Build status not yet verified. A workflow file being committed does not mean APK compilation succeeded.

## v0.1.4 development
- HTML adapters now discover standard public GET search forms and parse matching result links; site-specific forms/markup require verification.
- Unit tests enforce direct-download rights gating; CI now runs tests before assembling APK.
- No successful workflow run, installed APK, or MP3 download has yet been independently verified.
