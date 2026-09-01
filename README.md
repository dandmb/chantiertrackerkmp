# ChantierTracker — App Mobile / Desktop

Client **Kotlin Multiplatform** de ChantierTracker, application de suivi de
chantier à distance (rôles propriétaire / délégué).

Cette app consomme l'API REST existante de ChantierTracker
(`https://api.chantiertracker.com/api/v1`) ; le backend Spring Boot et le
frontend web React sont déjà en production. Architecture **online-first** : les
données viennent de l'API, pas de persistance locale.

## Stack

- **Compose Multiplatform** — UI partagée Android / iOS / Desktop
- **MVVM** — `ViewModel` + `StateFlow`, découpage `data / domain / presentation`
- **Koin** — injection de dépendances
- **Ktor** client — réseau (OkHttp sur Android/Desktop, Darwin sur iOS)
- **Navigation Compose** — navigation
- **kotlinx.serialization** / **kotlinx.coroutines**

Cibles : **Android** · **iOS** · **Desktop (JVM)**.

## Prérequis

- **JDK 21** pour Gradle (le daemon JVM est fixé à Java 21) :
  ```bash
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
  ```
- **Android** : SDK 36, un émulateur ou un appareil
- **iOS** : Xcode 26+ (macOS uniquement)

## Lancer

| Cible | Commande |
|---|---|
| Android | `./gradlew :androidApp:installDebug` puis lancer l'app (ou `./gradlew :androidApp:assembleDebug` → APK dans `androidApp/build/outputs/apk/debug/`) |
| Desktop | `./gradlew :desktopApp:run` |
| iOS | ouvrir `iosApp/iosApp.xcodeproj` dans Xcode et lancer, ou `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'id=<SIMULATOR_ID>' build` |

## Tests

```bash
./gradlew :shared:jvmTest                 # tests communs sur JVM + tests UI Compose
./gradlew :shared:testAndroidHostTest     # tests communs sur Android (host)
./gradlew :shared:iosSimulatorArm64Test   # tests communs sur simulateur iOS
./gradlew :shared:allTests                # rapport agrégé multiplateforme
```

## Structure

```
shared/       bibliothèque KMP — tout le code partagé (data / domain / presentation / di)
androidApp/   application Android
desktopApp/   application Desktop (JVM)
iosApp/       application iOS (projet Xcode + framework "Shared")
```

Architecture, décisions techniques et endpoints consommés : voir
`MOBILE_CONTEXT.md` (local, non versionné).
