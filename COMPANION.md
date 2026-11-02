# Companion Screen (AYN Thor)

A second-screen companion baked into the APK. On dual-screen handhelds (AYN Thor and
similar), the bottom display shows a tabbed companion UI instead of sitting idle; the
game keeps the top screen.

This is an `emerytech` fork feature, not in upstream `jaredgei/wow-forever-android`.

## What it shows

| Tab | Source | Live? |
| :-- | :-- | :-- |
| **Character** | In-game `WFCompanion` addon → SavedVariables | Refreshes on `/reload` or logout (Classic limitation) |
| **Maps** | Bundled zone reference + live zone/coords from the addon | Position updates with Character |
| **Talents** | Class talent-tree scaffold (keyed off the addon's class) | — |
| **Notes** | On-device checklist (`localStorage`) | Instant |

The on-screen keyboard is still reachable from the companion via the floating key
button (bottom-right), same as Hybrid mode.

## How the data path works

```
WoW (Wine)  →  WFCompanion addon  →  WTF/Account/<ACCT>/SavedVariables/WFCompanion.lua
                                          │  (writes WFCompanionJSON = "<json>")
Android app  ←  CompanionDataSource (polls mtime every 3s, unescapes the JSON string)
   │  evaluateJavascript(window.__wfUpdate(json))
   ▼
WebView (assets/companion/*)  →  renders Character / Maps / Talents
```

The addon encodes its state as a JSON **string** (not a Lua table) so the app needs no
Lua parser — it extracts the quoted literal and unescapes it.

### Why Character isn't continuously live

Classic flushes SavedVariables to disk only on `/reload` or logout. The addon keeps an
up-to-date value in memory and refreshes on key events, but the file (and therefore the
companion) updates at the next flush. Type `/wfc` in-game to publish + reload in one step,
or tap the ↻ button on the companion after a `/reload`. True continuous HUD would require
reading Wine process memory (bot/radar tech, against Blizzard ToS) — deliberately not done.

## The addon

Ships in `app/src/main/assets/companion-addon/WFCompanion/` and is auto-installed to
`<gameRoot>/_forever_/Interface/AddOns/WFCompanion/` on launch (`CompanionAddon.install`).
`checkAddonVersion "0"` is forced in `Config.wtf` so it loads despite the `.toc` interface
number not matching the live build.

To extend the data, edit `WFCompanion.lua` (`publish()` builds the JSON) and the matching
fields in `assets/companion/app.js` (`window.__wfUpdate`).

## Code map

- `externaldisplay/ExternalDisplayInputController.kt` — adds `Mode.COMPANION`, renders `CompanionView` on the presentation display.
- `externaldisplay/CompanionView.kt` — WebView host + SavedVariables poller + keyboard toggle.
- `ui/screen/wow/CompanionAddon.kt` — copies the addon into the game install.
- `assets/companion/` — the companion web UI (`index.html`, `style.css`, `app.js`).
- `assets/companion-addon/WFCompanion/` — the in-game addon.
- `com/winlator/container/Container.java` — `EXTERNAL_DISPLAY_MODE_COMPANION`.

Companion mode is set as the WoW container default in `WoWForeverScreen.containerConfig`.
On single-screen devices it's a harmless no-op (no presentation display found).

## Build

```bash
export JAVA_HOME=<path-to-jdk-17>
./gradlew :app:assembleModernDebug      # or assembleModernRelease with a keystore
```

Requires JDK 17, Android SDK platform 36, NDK 27.3.13750724 (see `app/build.gradle.kts`).
APK lands in `app/build/outputs/apk/modern/debug/`.
