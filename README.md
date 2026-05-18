# kmp-native-splash

Generate **native** splash screens for Android and iOS from a single Kotlin Gradle DSL — and keep them on screen at
runtime until your app is ready to draw. for Compose Multiplatform / KMP projects.

The project ships two pieces:

| Module      | Coordinates                                        | What it does                                                                                                  |
|-------------|----------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| **Plugin**  | `ly.com.tahaben.kmp-native-splash` (Gradle plugin) | Generates Android `res/drawable-*` + `values-*` and iOS `Assets.xcassets/` + `LaunchScreen.storyboard` assets |
| **Library** | `ly.com.tahaben:kmp-native-splash` (KMP runtime)   | `NativeSplash.preserve()` / `NativeSplash.remove()` to defer the first frame until you're ready               |

Together they replace the manual ritual of hand-rolling per-density PNGs, Android 12 `windowSplashScreen*` themes, iOS
launch storyboards, and `UILaunchStoryboardName` plumbing — for every flavor of every variant.

---

## Features

- **Single source of truth** — declare colors, foreground image, branding, and Android 12 icon in one
  `kmpNativeSplash { … }`
  block.
- **Dark mode** — supply `colorDark` / `imageDark` and the plugin writes a parallel `drawable-night-*` / dark-appearance
  asset tree.
- **Android 12+ splash API** — generates `drawable-*-v31` icons + `values-v31/styles.xml` with
  `windowSplashScreenAnimatedIcon`, `windowSplashScreenIconBackgroundColor`, `windowSplashScreenBrandingImage`.
- **Flavors** — `flavor("dev") { … }` produces per-flavor Android resources (AGP-integrated or source-set fallback) and
  per-flavor iOS storyboards / imagesets.
- **AGP variant integration** — when AGP is applied the plugin hooks `androidComponents.onVariants` and feeds outputs
  through `variant.sources.res.addGeneratedSourceDirectory`, so `processVariantAndroidResources` picks them up with zero
  extra wiring.
- **iOS Xcode auto-wiring (opt-in)** — `autoWireXcodeFlavors = true` patches `project.pbxproj` to register the
  per-flavor storyboards and set `LAUNCH_SCREEN_STORYBOARD` per `XCBuildConfiguration`, then points `Info.plist`'s
  `UILaunchStoryboardName` at `$(LAUNCH_SCREEN_STORYBOARD)`. Idempotent — deterministic SHA-1-derived object IDs.
- **Runtime control** — `NativeSplash.preserve()` keeps the native splash on screen; `NativeSplash.remove()` dismisses
  it (animated fade on iOS, `setKeepOnScreenCondition` flip on Android).
- **Configuration-cache friendly** — every task input is declared via `Property<T>` / `RegularFileProperty`; outputs are
  wired into `OutputDirectory` so incremental builds work.

---

## Installation

### 1. Apply the Gradle plugin

Apply it to the **KMP module** — the one with the `kotlin { }` multiplatform block and
`commonMain`. That is `composeApp` in the classic single-module structure, or `shared`
in the AGP 9 split structure (where the Android application module lives in a separate
`:androidApp`). The plugin auto-detects where the Android application module is; see
[AGP 9 split structure](#agp-9-split-structure).

```kotlin
// composeApp/build.gradle.kts  (or shared/build.gradle.kts on AGP 9)
plugins {
    id("ly.com.tahaben.kmp-native-splash") version "1.0.0"
}
```

### 2. Add the runtime library

```kotlin
// composeApp/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("ly.com.tahaben:kmp-native-splash:1.0.0")
        }
    }
}
```

The runtime is published for `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`, `jvm` (covers Compose Desktop's
`jvm("desktop")`), `js`, `wasmJs`, and `linuxX64`. JVM, Linux, and the web targets ship no-op actuals so commonMain
calls compile and link everywhere.

---

## Usage

### Generate the assets

```kotlin
// composeApp/build.gradle.kts
kmpNativeSplash {
    color = "#42a5f5"
    image = "splash_assets/logo.png" // supply a 4× asset — path resolves against the module directory
}
```

Run once:

```bash
./gradlew generateNativeSplash
```

Generated outputs:

```
build/generated/kmpnativesplash/default/res/   (Android — auto-fed to AGP if applied)
└── drawable-mdpi/   drawable-hdpi/   …   drawable-xxxhdpi/
    drawable-v21/    drawable-night/  drawable-*-v31/
    values/styles.xml + values-v31/styles.xml

iosApp/iosApp/
├── Assets.xcassets/LaunchImage.imageset/
├── Assets.xcassets/LaunchBackground.imageset/
└── Base.lproj/LaunchScreen.storyboard
```

Per-platform tasks are also exposed: `generateAndroidSplash`, `generateIosSplash`, plus `generate<Flavor>NativeSplash`
once flavors are declared.

### Defer the first frame at runtime

```kotlin
// Android: MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        NativeSplash.installSplashScreen(this)   // wires AndroidX SplashScreen
        super.onCreate(savedInstanceState)
        NativeSplash.preserve()                  // keep it on screen
        lifecycleScope.launch {
            initializeApp()
            NativeSplash.remove()                // dismiss
        }
    }
}
```

On iOS you can preserve from either side of the bridge. **Kotlin (iOS source set)** is the simplest path — no framework
export required:

```kotlin
// iosMain: MainViewController.kt
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    NativeSplash.preserve()
    App()
}
```

**Swift** works too, but the runtime must be re-exposed in the produced framework's public header. In your
`composeApp/build.gradle.kts`, declare the dependency as `api` and add `export(...)` on every iOS framework binary:

```kotlin
kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            export("ly.com.tahaben:kmp-native-splash:1.0.0")
        }
    }
    sourceSets {
        commonMain.dependencies {
            api("ly.com.tahaben:kmp-native-splash:1.0.0")
        }
    }
}
```

Then preserve from your SwiftUI entry point. `.onAppear` on the `WindowGroup` root fires once the key window exists,
which is what `NativeSplash` needs:

```swift
// iosApp.swift

import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    NativeSplash.shared.preserve()
                }
        }
    }
}
```

> **Note:** without the `api` + `export(...)` wiring above, `NativeSplash` will not appear in the `ComposeApp`
> framework's Swift surface — `import ComposeApp` will succeed but the symbol won't resolve. The Kotlin-side approach
> above sidesteps this entirely.

In Compose Multiplatform commonMain code, dismiss the splash once you're ready to draw:

```kotlin
@Composable
fun App() {
    LaunchedEffect(Unit) {
        warmCachesAndPreload()
        NativeSplash.remove()
    }
    // … your UI …
}
```

---

## Customization

### Full DSL surface

```kotlin
kmpNativeSplash {
    // ── Project coordinates ────────────────────────────────────────────────
    // AGP 9 split only; omit for the classic single-module structure (auto-detected).
    // Gradle project path of the standalone com.android.application module.
    androidModule = ":androidApp"
    useGeneratedSourceSet = true // AGP path; flip to false for source-set writes

    android {
        // Path strings. Relative paths resolve against this module's directory.
        resRoot = "src"                                       // default: "src"
        manifest = "src/androidMain/AndroidManifest.xml"      // default: "src/androidMain/AndroidManifest.xml"
    }
    ios {
        // Path to the iOS project directory, relative to this module (default: "../iosApp")
        projectPath = "../iosApp"
        // Name of the Xcode target for the iOS app (default: "iosApp")
        targetName = "iosApp"
        // iOS app source folder. Defaults to `<projectPath>/<targetName>` — set explicitly
        // only if your layout deviates.
        // appDir = "../iosApp/iosApp"
    }

    // ── Default variant ────────────────────────────────────────────────────
    // Image paths are plain strings. Relative paths resolve against the module
    // directory; absolute paths are used as-is.
    color = "#42a5f5"
    colorDark = "#042a49"
    image = "splash_assets/logo.png"         // 4× source
    imageDark = "splash_assets/logo_dark.png"
    backgroundImage = "splash_assets/bg.png" // mutually exclusive with `color`
    fullscreen = false

    // ── Platform-specific overrides ────────────────────────────────────────
    android {
        gravity = "center"            // any android:gravity value
        screenOrientation = "portrait"
    }
    ios {
        contentMode = "scaleAspectFit"
        infoPlistFiles.addAll("Info-Debug.plist", "Info-Release.plist")
    }

    // ── Branding image (rendered in addition to the foreground) ────────────
    branding {
        image = "splash_assets/brand.png"
        mode = BrandingMode.BOTTOM    // BOTTOM | BOTTOM_LEFT | BOTTOM_RIGHT
        bottomPadding = 24
    }

    // ── Android 12+ SplashScreen API ───────────────────────────────────────
    android12 {
        color = "#42a5f5"
        image = "splash_assets/a12_logo.png"
        iconBackgroundColor = "#111111"
        brandingImage = "splash_assets/brand.png"
    }

    // ── Platform toggles ───────────────────────────────────────────────────
    platforms {
        android = true
        ios = true
    }

    // ── Per-flavor overrides (delta against the default variant) ───────────
    flavor("dev") {
        color = "#222222"
        image = "splash_assets/logo_dev.png"
    }
    flavor("prod") {
        image = "splash_assets/logo_prod.png"
        android12 { image = "splash_assets/a12_prod.png" }
    }
}
```

Per-flavor `generateDevAndroidSplash`, `generateDevIosSplash`, `generateDevNativeSplash` are registered automatically.

### AGP integration

When `com.android.application`, `com.android.library`, or `com.android.kotlin.multiplatform.library` is applied, the
plugin hooks `androidComponents.onVariants`:

```kotlin
android {
    productFlavors {
        create("dev")
        create("prod")
    }
}
kmpNativeSplash {
    flavor("dev") { color = "#ffffff" }
    flavor("prod") { color = "#42a5f5" }
}
```

Yields `generateDevDebugAndroidSplash`, `generateProdReleaseAndroidSplash`, etc., each wired into the matching
`processVariantAndroidResources` via `variant.sources.res.addGeneratedSourceDirectory`. **No `sourceSets {}` edits
required.**

### AGP 9 split structure

Classic KMP projects keep the `kotlin { }` multiplatform extension **and** the
`com.android.application` + `productFlavors` in one `composeApp` module. AGP 9 splits
these: shared/generated code stays in the KMP module (`shared`, applying
`com.android.kotlin.multiplatform.library`), while the Android application and its
product flavors move to a standalone `:androidApp` module (`com.android.application`),
because the KMP-library plugin can't host `productFlavors`.

Apply this plugin to the **KMP module** in both layouts. It then resolves where the
Android application module is, in strict precedence:

1. **Explicit override** — `androidModule = ":androidApp"` always wins.
2. **Auto-detect** — the single module applying `com.android.application` (a module
   that also applies the KMP-library plugin is excluded).
3. **Fallback** — the applied module itself (classic monolith / no-Android). This is
   the zero-config legacy path and is byte-for-byte unchanged.

Only the Android-scoped work is redirected to the resolved module — the generated
`res/` tree and the `AndroidManifest.xml` patch. Source assets you reference in the
DSL (`image`, `branding.image`, `android12.image`, …) keep resolving against the
**applied module** (where the DSL and your `splash_assets/…` live). iOS is unaffected;
its location is controlled by `ios.projectPath` / `ios.targetName` as before.

```kotlin
// shared/build.gradle.kts
kmpNativeSplash {
    // Standard AGP 9 layout (single :androidApp) needs nothing — auto-detected.
    // Set this only for non-standard layouts (multiple app modules / custom names):
    androidModule = ":androidApp"
    color = "#42a5f5"
    image = "splash_assets/logo.png" // read from shared/, written into androidApp/
}
```

Source-set naming follows the **standalone Android app** convention in the split,
because `:androidApp` is a plain `com.android.application` module (no KMP
`androidMain` source set):

```
androidApp/src/main/res/    ← default variant   (classic composeApp: src/androidMain/res/)
androidApp/src/dev/res/     ← flavor "dev"
androidApp/src/prod/res/    ← flavor "prod"
```

Manifest: in the split, the launcher-activity manifest lives in `:androidApp`
(usually `src/main/AndroidManifest.xml`). The plugin resolves the configured
`android.manifest` against the Android module and, if absent there, falls back to
`src/main/AndroidManifest.xml` — so the standard split needs no `android.manifest`
override.

Resolution logs one lifecycle line stating what it found. Ambiguous detection
(multiple `com.android.application` modules) or an unresolvable explicit
`androidModule` **warns at configuration time and fails only the
`generate*AndroidSplash` task** (with the candidate list / fix), never `build` or
codegen. `useGeneratedSourceSet = true` isn't supported across the AGP 9 module
boundary — in a true split the plugin writes into the resolved module's source set
instead and warns once.

### iOS multi-flavor Xcode wiring (opt-in)

```kotlin
kmpNativeSplash {
    ios {
        autoWireXcodeFlavors = true
        // Both default to the stock KMP layout; override only if yours deviates.
        // The `.xcodeproj` path is derived as `<projectPath>/<targetName>.xcodeproj`.
        projectPath = "../iosApp"
        targetName = "iosApp"
    }
    flavor("dev") { /* … */ }
    flavor("prod") { /* … */ }
}
```

Running `./gradlew generateNativeSplash` then patches `project.pbxproj`:

- Adds `PBXFileReference` + `PBXBuildFile` + Resources-phase entry + `Base.lproj` group child for every
  `LaunchScreen<Flavor>.storyboard` (or **reuses** the existing reference if one is present).
- Sets `LAUNCH_SCREEN_STORYBOARD = "LaunchScreen<Flavor>";` in every `XCBuildConfiguration`'s `buildSettings`, mapping
  config names like `Debug-dev` → `LaunchScreenDev` via fuzzy suffix matching.
- Rewrites `Info.plist`'s `UILaunchStoryboardName` to `$(LAUNCH_SCREEN_STORYBOARD)` so the per-config setting routes the
  right storyboard at build time. If the project uses `GENERATE_INFOPLIST_FILE = YES` /
  `INFOPLIST_KEY_UILaunchStoryboardName` (no on-disk `Info.plist`), it routes that build-setting key through the same
  `$(LAUNCH_SCREEN_STORYBOARD)` indirection instead.

All edits are idempotent — re-running produces zero new entries. **Commit your Xcode project before enabling this.**

> **Prerequisite — per-flavor build configurations + schemes (the plugin does not create these).**
> iOS has no product flavors. A per-flavor launch screen is selected by the **Xcode build
> configuration** that is active when the app runs. A stock KMP `iosApp` ships only `Debug`
> and `Release`, so without setup *every* flavor shows the default `LaunchScreen`. You must,
> in Xcode (or via a flavor plugin like [KMP-Flavorizr](https://github.com/tahaak67/KMP-Flavorizr)), have per-flavor
> build configurations + a scheme per
> flavor that builds with them. The plugin maps a configuration to its storyboard by the
> flavor token in its name (the build-type token never wins), so all of these resolve
> correctly: `Debug-dev` / `Release-dev`, `devDebug` / `prodRelease` / `devProfile`
> ([KMP-Flavorizr](https://github.com/tahaak67/KMP-Flavorizr) / xcconfig style),
> `Dev Debug`, `PROD_RELEASE`. It only patches configurations that already exist — it
> does not create them.
>
> **Troubleshooting: "it always shows the default launch storyboard regardless of flavor."**
> The per-flavor `LaunchScreen<Flavor>.storyboard` files generate and look correct in Xcode,
> but the app always launches with the default. Causes, in order of likelihood:
> 1. **Xcode wiring is opt-in and disabled.** Check the Gradle log: if
     > `> Task …:wireXcodeFlavors SKIPPED` (or the iOS task warns that Xcode is NOT wired),
     > then `kmpNativeSplash.ios.autoWireXcodeFlavors` is `false` (the default — it patches
     > `project.pbxproj`, so it's off until you opt in). Enable it:
     >    ```kotlin
     > kmpNativeSplash { ios { autoWireXcodeFlavors = true } }
>    ```
     >    Commit your Xcode project first. Without this, the per-flavor storyboards are
     > generated but never connected to any build configuration.
> 2. **No per-flavor build configurations** (the prerequisite above). With wiring enabled,
     > `./gradlew generateNativeSplash` logs each `config '<name>' → <storyboard>` mapping and
     > warns loudly when every configuration resolved to the default. If you only see
     > `Debug`/`Release`, add the per-flavor configs.
> 3. **`GENERATE_INFOPLIST_FILE = YES`** — the on-disk `Info.plist` edit is ignored by Xcode.
     > The plugin also routes `INFOPLIST_KEY_UILaunchStoryboardName` through
     > `$(LAUNCH_SCREEN_STORYBOARD)`; ensure that key exists in the target's build settings.
> 4. **iOS launch-screen cache** — iOS caches the launch screen aggressively. After the fix,
     > delete the app from the simulator/device (or erase the simulator) and rebuild.

### Source-set fallback (no AGP)

When the project doesn't apply AGP, or when `useGeneratedSourceSet = false`:

```kotlin
kmpNativeSplash { useGeneratedSourceSet = false }
```

Output flips to the on-disk source-set path:

```
src/androidMain/res/   ← default variant
src/dev/res/           ← flavor "dev"
src/prod/res/          ← flavor "prod"
```

These paths are relative to the resolved Android module. In the classic single-module
structure that's the applied module (`src/androidMain/res/`). In the AGP 9 split it's
`:androidApp`, a standalone `com.android.application` module, so the default variant
uses the standard Android source set instead — `androidApp/src/main/res/` (flavors stay
`androidApp/src/<flavor>/res/`). See [AGP 9 split structure](#agp-9-split-structure).

### Runtime knobs

```kotlin
// iOS-only: point at a different storyboard if you don't use the plugin-owned one
NativeSplash.storyboardName = "MyCustomLaunchScreen"
```

```kotlin
// Android-only: hook the SplashScreen exit animation
NativeSplash.installSplashScreen(this) { splashScreenViewProvider ->
    splashScreenViewProvider.view.animate()
        .alpha(0f).setDuration(300)
        .withEndAction { splashScreenViewProvider.remove() }
        .start()
}
```

---

## Tasks reference

| Task                             | What it does                                                                      |
|----------------------------------|-----------------------------------------------------------------------------------|
| `generateNativeSplash`           | Umbrella — runs every platform × every variant.                                   |
| `generateAndroidSplash`          | Default variant Android pipeline.                                                 |
| `generateIosSplash`              | Default variant iOS pipeline.                                                     |
| `generate<Variant>AndroidSplash` | Per-flavor (or per-AGP-variant) Android pipeline.                                 |
| `generate<Variant>IosSplash`     | Per-flavor iOS pipeline.                                                          |
| `generate<Variant>NativeSplash`  | Per-flavor Android + iOS aggregator.                                              |
| `wireXcodeFlavors`               | Opt-in pbxproj + Info.plist patcher. Runs as `finalizedBy(generateNativeSplash)`. |

---

## Compatibility

| Tool    | Version      |
|---------|--------------|
| Gradle  | 8.5+         |
| Kotlin  | 2.0+         |
| AGP     | 8.2+         |
| JDK     | 17+          |
| Android | `minSdk` 24+ |
| iOS     | iOS 11+      |

---

## License

Apache License 2.0

## Contributing

Contributions are welcome, please open a branch and submit a PR.

KMP-native-splash is inspired by  [flutter_native_splash](https://pub.dev/packages/flutter_native_splash).