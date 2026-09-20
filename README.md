# StealthCam

Background camera + microphone recorder (`com.venom.stealthcam`, minSdk 31) that keeps recording with the screen off.

## How it works

- **Volume-key trigger** — `core/trigger/VolumeTriggerManager.kt` + `core/trigger/VolumeKeyAccessibilityService.kt`: start/stop recording from the volume keys, no screen needed.
- **Background capture** — `data/camera/` behind a foreground service (`FOREGROUND_SERVICE_CAMERA` + `FOREGROUND_SERVICE_MICROPHONE`, Android 12+); `WAKE_LOCK` keeps the CPU alive while the screen is off.
- **OEM survival** — requests battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, see manifest notes for MIUI/OneUI/EMUI) so the system doesn't suspend the service.
- Layers: `core/` · `data/` · `domain/` · `ui/` · `di/` · `extension/`.

## Stack

Kotlin · Jetpack Compose · Hilt · Camera2/CameraX (`data/camera`) · AccessibilityService · Foreground services.

## Build

Open in Android Studio (minSdk 31) and run. Grant camera + microphone + notifications; approve the battery-exemption prompt for screen-off recording.
