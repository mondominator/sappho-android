# Sappho

Native Android client for the [Sappho](https://github.com/mondominator/sappho) audiobook server. Built with Kotlin and Jetpack Compose.

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.sappho.audiobooks">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="200"/>
  </a>
</p>

## Highlights

- **Chromecast** — Cast to any Cast-enabled device
- **Android Auto** — Full browsing and playback while driving
- **Offline mode** — Download books for listening without connectivity
- **Catch Me Up** — AI-generated series recaps before starting the next book
- **Admin tools** — Library scanning, user management, and AI configuration from mobile

## Requirements

- Android 8.0+ (API 26)
- A running Sappho server instance

## Installation

Download from [Google Play](https://play.google.com/store/apps/details?id=com.sappho.audiobooks) or grab the APK from [Releases](../../releases).

On first launch, enter your server URL and credentials.

## Building from source

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Requires Android Studio, JDK 17, and Android SDK API 34.

## Architecture

MVVM + Clean Architecture with Hilt DI. Jetpack Compose UI, Retrofit networking, Media3 for playback, Coil for image loading.

```
app/src/main/java/com/sappho/audiobooks/
├── data/           # API services, repositories
├── domain/         # Models
├── presentation/   # Screens, ViewModels, theme
├── service/        # Background playback
├── cast/           # Chromecast integration
├── download/       # Offline download manager
└── di/             # Dependency injection
```

## CI/CD

GitHub Actions builds on every push to `main` and deploys to Google Play internal testing. Tagged releases (`v*`) publish APKs to GitHub Releases.

## Contributors

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="25%"><sub><b>Roberto Diaz</b></sub><br /><sub>🧪 Testing</sub></td>
      <td align="center" valign="top" width="25%"><sub><b>Kev Beaudin</b></sub><br /><sub>🧪 Testing</sub></td>
      <td align="center" valign="top" width="25%"><sub><b>Chad Beaudin</b></sub><br /><sub>🧪 Testing</sub></td>
      <td align="center" valign="top" width="25%"><sub><b>Sara Johnson</b></sub><br /><sub>🧪 Testing</sub></td>
    </tr>
  </tbody>
</table>

<!-- ALL-CONTRIBUTORS-LIST:END -->

## License

MIT
