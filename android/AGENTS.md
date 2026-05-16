# AI Coding Agent Guide for CallCenterApp

## What this project is
- Android app in Kotlin using Jetpack Compose, Hilt, Room, Retrofit, DataStore, Coroutines, and Kotlin Serialization.
- Single Gradle module app at `:app`.
- Architecture is MVVM / Clean Architecture with a clear data layer, DI layer, UI layer, service layer, and background call service.

## Key files and folders
- `app/build.gradle.kts` — Android and dependency configuration.
- `local.properties` — contains `VERSION_CODE`, `VERSION_NAME`, and `DEFAULT_SERVER_URL`.
- `app/src/main/java/com/callcenter/app/di/` — Hilt modules for network and database wiring.
- `app/src/main/java/com/callcenter/app/data/api/` — Retrofit API services.
- `app/src/main/java/com/callcenter/app/data/local/` — Room DAOs, entities, DataStore preferences.
- `app/src/main/java/com/callcenter/app/data/repository/` — repository implementations.
- `app/src/main/java/com/callcenter/app/ui/` — compose screens, navigation, themes, viewmodels.
- `app/src/main/java/com/callcenter/app/service/` — background auto-dial and call monitor services.
- `app/src/main/java/com/callcenter/app/receiver/` — phone state and boot receivers.
- `doc/通话状态知识库.md` — call-status business rules and state definitions.

## Build and run commands
Use the workspace root for Gradle commands.
- `./gradlew assembleDebug`
- `./gradlew test`
- `./gradlew connectedAndroidTest` (device/emulator required)
- `./gradlew lint`

## Important conventions
- `local.properties` controls runtime configuration.
  - `DEFAULT_SERVER_URL` is normalized and auto-appends `/api/` in `app/build.gradle.kts`.
- API base URL should be read from `BuildConfig.DEFAULT_SERVER_URL`, not hard-coded.
- UI work belongs in `ui/screens/`, navigation in `ui/navigation/`, and state in `ui/viewmodel/`.
- Data access should use `data/api/`, `data/local/`, and `data/repository/`.
- Dependency injection bindings belong in `di/DatabaseModule.kt` and `di/NetworkModule.kt`.
- Background call handling and telephony logic are in `service/` and `receiver/`.

## Project-specific guidance
- Prefer Compose-based UI over legacy XML because the app already uses Material 3 and Compose.
- Keep network models and repository abstractions separate from UI code.
- When adding new API endpoints, update Retrofit interfaces under `data/api/` and corresponding repository methods.
- When introducing new local storage behavior, mirror it in `data/local/dao`, `data/local/entity`, and repository layers.
- Use `CallCenter` tag for Logcat consistency where relevant.

## Domain notes to keep in mind
- The app’s call flow is business-critical: dial, ring, active, disconnect states are important.
- Refer to `doc/通话状态知识库.md` for valid call-result states and how call status should be represented.
- The app assumes backend endpoints under `/api/`, such as `/api/auth/login`, `/api/customers`, `/api/calls`, `/api/stats/dashboard`.

## Where to look first for related tasks
- Feature/UI changes: `app/src/main/java/com/callcenter/app/ui/`
- Network/API work: `app/src/main/java/com/callcenter/app/data/api/`
- Offline storage: `app/src/main/java/com/callcenter/app/data/local/`
- DI and app wiring: `app/src/main/java/com/callcenter/app/di/`

## When you need extra context
- Read `README.md` for environment setup, device/emulator usage, and permissions.
- Read `doc/通话状态知识库.md` for business logic around call statuses.
