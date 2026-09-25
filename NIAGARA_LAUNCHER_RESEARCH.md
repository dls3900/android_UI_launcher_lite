# Niagara-style Android launcher: research and build brief

Research date: 25 September 2026. This workspace was empty when inspected. Android Studio, the Android SDK, `adb`, Java, and Git are installed; a standalone `gradle` command is not, so a project should include the Gradle wrapper.

## Goal and reference

Build a native Android home replacement with the same visual structure and interaction feel as Niagara's home screen, while keeping startup, scrolling, and app launch responsive. Niagara has configurable clock, fonts, icons, wallpaper, widgets, and clock position, so an **exact** visual target needs one screenshot, device resolution/density, Android version, theme, and wallpaper. Until those are supplied, use the current default Niagara appearance as a provisional reference. Match dimensions from the screenshot rather than assuming fixed dp values.

Niagara's own overview describes a clean list interface, one-handed alphabet navigation with a wave animation, and a customizable wallpaper/widget/icon system: [official press kit](https://help.niagaralauncher.app/article/83-press-kit).

## Home screen anatomy

| Element | Observed behavior / visual role | Initial build target |
| --- | --- | --- |
| Wallpaper | Full-screen background, optionally dimmed for readable text. | Use Android wallpaper behind transparent launcher UI, with configurable tint. |
| Status and navigation regions | Android system information at the top; gesture/navigation area at the bottom. | Render edge to edge and apply insets to interactive content. |
| Clock block | Prominent time with date; optional battery, weather, and next event. It can move vertically, carrying the remaining home content with it. | Time/date first. Keep a single movable anchor for clock + favorites. |
| Favorites | Short vertical list of app icon + name rows, left aligned in the common right-hand layout. Frequently used suggestions may appear below. | User-selected ordered favorites, native app icons, stable row height and touch target. |
| Alphabet rail | Slim letter index at the right edge by default, or left in left-hand mode. Dragging/tapping navigates the all-apps list. | One-handed scrub gesture, visual selected letter, haptic feedback, fast jump. |
| All-apps state | Apps grouped by initial letter; within each group Niagara defaults to usage ordering, with an alphabetical option. Locale affects grouping. | Group and search installed launchable apps; start alphabetically, add usage ordering later. |
| App pop-up | Swipe right on an app for shortcuts and notifications; long press manages favorites. | Shortcut pop-up in second phase; notification content only after explicit access. |
| Search and optional button | Search is another route to an app; optional Niagara button triggers a configured action. | Search in first phase; optional button later. |

Sources: [alphabet settings](https://help.niagaralauncher.app/article/171-alphabet-settings), [tips and gestures](https://help.niagaralauncher.app/article/34-tips-and-tricks), [move clock](https://help.niagaralauncher.app/article/156-move-clock), [custom clock](https://help.niagaralauncher.app/article/23-custom-clock), [pop-ups](https://help.niagaralauncher.app/article/115-pop-ups), [suggestions](https://help.niagaralauncher.app/article/103-suggestion-algorithm), [Niagara button](https://help.niagaralauncher.app/article/116-niagara-button).

### Screenshot matching procedure

1. Fix a reference phone size, display density, font scale, wallpaper, light/dark theme, and status/navigation mode.
2. Record clock top/left position, font size and weight, line spacing, icon diameter, label baseline, row pitch, side padding, alphabet position, and bottom clearance as proportions of usable screen size.
3. Put a reference screenshot and a launcher screenshot side by side at the same pixel size. Compare overlays/difference images for layout, text, icons, translucency, and gesture state.
4. Tune the common portrait case first, then verify small screens, large screens, landscape, tablets/foldables, and left-hand mode. Niagara scales its layout and may use two columns on wide devices: [foldable support](https://help.niagaralauncher.app/article/100-foldable-device-support).

Do not use Niagara's name, logo, bundled icon designs, or official wallpapers as this app's identity or bundled assets unless their use is licensed. An independent visual implementation can use system app icons and a user-selected wallpaper. This is a product/IP precaution, not a claim that the general list layout is protected.

## Android implementation map

| Requirement | Android facility | Notes |
| --- | --- | --- |
| Become Home | Exported `ACTION_MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT` activity; request `RoleManager.ROLE_HOME` where available. | User selects the default home app. [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager), [intent filters](https://developer.android.com/guide/components/intents-filters) |
| Enumerate and launch apps | `LauncherApps.getProfiles()`, `getActivityList()`, `startMainActivity()` and package callbacks. | Supports launchable activities and visible work profiles; use component + user as stable identity. [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps) |
| Package visibility | Manifest `<queries>` for `ACTION_MAIN` / `CATEGORY_LAUNCHER` if required for chosen queries. | Avoid broad `QUERY_ALL_PACKAGES` unless genuinely necessary and policy justified. [Visibility guide](https://developer.android.com/training/package-visibility/declaring) |
| App shortcuts | `LauncherApps.getShortcuts()` and shortcut launch methods. | Shortcut host permissions depend on default launcher status. [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps) |
| Host third-party widgets | `AppWidgetHost` / `AppWidgetManager` with bind/configure flow. | Add after core home is stable. [Widget host guide](https://developer.android.com/develop/ui/views/appwidgets/host) |
| Notification previews | `NotificationListenerService`. | Optional, requires user-granted notification access. [Notification listener](https://developer.android.com/reference/android/service/notification/NotificationListenerService) |
| Usage-based suggestions | `UsageStatsManager`, or a launcher-local launch counter. | Prefer local launch counter initially; device-wide usage needs special user access. [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) |
| Edge-to-edge layout | Window insets and transparent system bars. | Android 15+ enforces edge-to-edge for target SDK 35+. [Android 15 behavior](https://developer.android.com/about/versions/15/behavior-changes-15) |

## Suggested architecture

- Kotlin native Android app. Jetpack Compose is suitable for the clock, favorites, search, settings, and pop-ups; keep the alphabet gesture/input path lean. Use `LazyColumn` with stable keys for all apps. A custom View is an option only if measured Compose performance misses the target.
- A launcher repository observes `LauncherApps` callbacks, builds immutable app entries off the main thread, sorts/groups once per change, and exposes cached snapshots to the UI.
- Persist favorites, theme, clock position, and optional actions in DataStore. Use a small local database only if history, folders, or complex per-app configuration grows.
- Cache icon drawables/bitmaps by component, user, density, and theme; invalidate on package/icon changes. Avoid blocking icon loads, package scans, wallpaper reads, or sorting during composition or touch handling.
- Keep system wallpaper handling separate from the home UI. Preserve readable text using a calculated scrim/tint and a manual override.
- Use lifecycle-aware clock ticks and listeners. Do not keep a background service running just to render Home.

Android's Compose guidance recommends stable lazy-list keys, moving sorts out of composition, and limiting recomposition; measure a release build rather than judging debug scrolling. [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices), [lazy lists](https://developer.android.com/develop/ui/compose/lists).

## Build order

1. **Working home replacement:** project + manifest/role request, installed app enumeration/launch, wallpaper, clock/date, ordered favorites, alphabet rail, search, and persistent settings.
2. **Visual match:** tune the selected screenshot's geometry, typography, icon treatment, gestures, and transitions on the target device; support light/dark and left-hand modes.
3. **Launcher depth:** shortcuts and pop-ups, suggestions, widgets, optional weather/calendar/media/notifications, icon packs, and wide-screen behavior. Niagara's widget stack and pop-up widget features are documented separately: [widgets](https://help.niagaralauncher.app/article/41-adding-multiple-widgets), [media](https://help.niagaralauncher.app/article/11-media-widget).
4. **Optimization:** profile cold Home entry, returning Home, alphabet dragging, long app lists, wallpaper changes, package installs/removals, and low-memory recovery on a real device. Build a Baseline Profile for measured hot paths. Aim for animation work within the display frame budget (roughly 16 ms at 60 Hz, 8.3 ms at 120 Hz), and set device-specific startup/memory goals after baseline measurements. [Android rendering](https://developer.android.com/topic/performance/issues/render), [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview).

## Acceptance checklist for the first build

- Can be selected as the default Home app and reliably returns from other apps.
- Shows every launchable personal-profile app, launches the selected app, and updates after install/uninstall.
- Favorites persist across process death and reboot; search and alphabet reach any indexed app.
- Home matches the chosen screenshot's major positions and sizes at its reference resolution.
- No noticeable stalls during alphabet drag or app launch; verify with release build traces and frame metrics.
- Handles missing permissions, wallpaper contrast, display cutouts, font scaling, and empty app/favorites states.

## Open input for exact visual match

Provide one screenshot of the specific Niagara home screen to copy and the phone model or screen resolution. If the screenshot includes a custom wallpaper/icon pack/font, provide those assets or choose similar independently licensed alternatives.
