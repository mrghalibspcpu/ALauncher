# A Launcher

A complete, gesture-first Android home screen written from scratch in plain Java —
no Gradle, no AndroidX, no third-party libraries. Every pixel is custom drawn, including
its **own Control Center**.

**Installable APK:** [`dist/ALauncher.apk`](dist/ALauncher.apk) (signed, minSdk 24 / Android 7+, targetSdk 34)

---

## Install

1. Copy `dist/ALauncher.apk` to the phone.
2. Open it → allow "Install unknown apps" if prompted → Install.
3. Press Home → choose **A Launcher** → *Always*.
   (Or: A Launcher → Settings → "Set A Launcher as default".)
4. First launch shows the gesture card. Recommended extras:
   * **Notification access** → icon badges + media card in Control Center
   * **Modify system settings** → brightness & auto-rotate tiles
   * **Do Not Disturb access** → DND tile
   * **Screen lock permission** → double-tap-to-lock

---

## Everything a launcher should have

| Area | What's in it |
| --- | --- |
| Home | Paged cell grid (3–7 × 4–8), up to 6 pages, page dots, wallpaper scroll, big thin clock |
| Icons | Adaptive-icon aware, 5 shapes (squircle / circle / rounded / teardrop / hexagon), auto-generated gradient plates for legacy icons, soft shadows, 70–140 % size |
| Drawer | Vertical grid, A–Z sections, alphabet rail, instant fuzzy + initials search, labels toggle |
| Folders | Drag icon onto icon → folder, auto-named, renameable, glass folder sheet |
| Widgets | Real `AppWidgetHost` — pick, bind, configure, place, drag, remove |
| Dock | 3–6 slots, drag in/out, folders allowed |
| Drag & drop | Lift, swap, cross-page flip at the edges, drop bar with Remove / Hide / Uninstall / Info |
| App menu | Open, split screen, add to home, Orbit, rename, mark distracting, hide, info, uninstall |
| Badges | Notification dots with counts, from a real `NotificationListenerService` |
| Hidden apps | Hide from drawer + restore list in Settings |
| Gestures | Swipe up / down, two-finger tap, double tap, long press, edge page flip |
| Theming | 10 accents, dynamic accent extracted from the last opened app's icon, blur-behind panels |
| Settings | One-scroll control of grid, drawer, sizes, shapes, toggles, permissions and lists |

## …and things no other launcher has

* **A Control Center** — the launcher's own quick-settings panel. Glass tiles for Wi-Fi,
  Bluetooth, mobile data, airplane, torch, auto-rotate, DND and Focus Mode; vertical
  brightness and volume sliders; a live media card (art, title, prev/play/next) driven by
  the real media session; battery ring with temperature, RAM and storage vitals.
  Swipe down on the right half of the home screen.
* **Glyph Launch** — press and hold for a moment on empty space, then *draw a letter*.
  A built-in $1-style unistroke recogniser (C G J L M N O P S T U V W Z) opens the app you
  use most starting with that letter. `O` opens Orbit, `S` opens search.
* **Orbit** — a thumb-radial launcher. Two-finger tap anywhere; favourites arc around your
  finger, slide and release (or just tap) to launch.
* **Flow Dock** — dock slots you leave empty are filled by on-device prediction:
  frequency + recency + *this hour of the day* habit + apps that currently have
  notifications. It literally changes through the day.
* **Time Machine** — a 24-hour histogram of your own launcher usage plus "what you usually
  open around this hour".
* **Focus Mode** — mark apps as distracting; one tap pauses them for 30 minutes. They grey
  out on home and in the drawer and refuse to open, with a countdown chip on the clock.
* **Command palette** — type `:` in the drawer search for `torch`, `focus`, `wifi`,
  `wallpaper`, `widgets`, `timeline`, `orbit`, `lock`, `restart`, `web …` and more.
* **Inline maths** — type `12.5%4` or `=(5+3)/2^2` in search and the answer appears instantly.
* **Dynamic accent** — the whole UI re-tints itself to the dominant colour of the last app
  you opened (hand-written palette extraction, no library).
* **Ambient layer** — drifting particle field and an accent glow that parallax with the
  accelerometer, so the wallpaper feels alive without replacing it.
* **Split launch** — long press any app → *Open in split screen* next to the current one.

---

## Gesture cheat sheet

| Gesture | Action |
| --- | --- |
| Swipe up | App drawer + search |
| Swipe down (right half) | A Control Center |
| Swipe down (left half) | System notification shade |
| Two-finger tap | Orbit radial launcher |
| Hold, then draw a letter | Glyph Launch |
| Double tap empty space | Lock screen |
| Long press empty space | Wallpaper / widget / Time Machine / Focus / settings |
| Long press an icon | Full app menu, or drag it |
| Drag icon onto icon | Create a folder |
| Drag to the top bar | Remove · Hide · Uninstall · Info |
| Tap the clock | Alarms |

---

## Build it yourself

The repo builds **without Gradle**. `build.sh` runs
`aapt2 compile → aapt2 link → javac → d8 → zipalign → apksigner`.

```bash
# needs: JDK 17 at $JAVA_HOME, Android SDK with build-tools 34 + platform 34
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK=/path/to/sdk
./build.sh            # → dist/ALauncher.apk
```

A debug keystore is generated on first run into `.keystore/` (not committed).

## Source map

```
app/AndroidManifest.xml
app/res/…                  icon (adaptive vector), theme, device-admin policy
app/src/com/alauncher/
  Launcher.java            home activity, surfaces, widgets, menus, commands
  HomeView.java            pages, cells, folders, dock, drag & drop, glyph canvas
  DrawerView.java          drawer grid, search, rail, calculator, palette
  ControlCenter.java       tiles, sliders, media card, vitals
  RadialMenu.java          Orbit
  ActionSheet.java         glass popup menus
  AppStore.java            app index, usage model, prediction, search, launching
  IconFactory.java         adaptive/legacy icon normalisation, shapes, mono
  Gesture.java             unistroke glyph recogniser
  Glyphs.java              hand-drawn vector icon set
  Calc.java                expression parser
  Commands.java            ":" palette
  Focus.java               Focus Mode
  Prefs.java / AppItem.java / U.java
  NotifService.java        badges + media sessions
  AdminReceiver.java       double-tap-to-lock
SettingsActivity.java      every preference, built in code
```

No images are shipped: all artwork (app icon, every glyph, folder plates, gradients) is
drawn by vector code at runtime, which is why the whole launcher is an **85 KB APK**.
