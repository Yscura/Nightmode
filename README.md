# Night Mode

A tiny, free, ad-free Android app that makes your tablet **quieter than its
lowest volume step** and **darker than its lowest brightness**. It uses no
internet, collects no data, and has no external libraries.

## Build and install (about 20 minutes, one time)

1. **Install Android Studio** (free) on your PC:
   https://developer.android.com/studio
2. **Open the project:** unzip `NightMode.zip`, start Android Studio, choose
   *Open*, and select the `QuietVolume` folder. Wait for "Gradle sync" to
   finish. The first time, it downloads what it needs. If it asks to install an
   SDK or update the Gradle plugin, accept.
3. **Prepare the tablet:** Settings → About tablet → tap *Build number* 7 times
   to unlock Developer options. Then turn on **USB debugging** in Developer
   options.
4. **Connect the tablet** by USB and accept the "Allow USB debugging?" prompt.
   (Windows: if the tablet doesn't show up, install the *Google USB Driver* from
   Android Studio's SDK Manager → SDK Tools, or Acer's driver.)
5. Pick the tablet in the device list at the top of Android Studio and press
   the green **Run ▶** button. The app is installed and opens.

*No cable?* Use Build → Build App Bundle(s) / APK(s) → **Build APK(s)**, copy
`app/build/outputs/apk/debug/app-debug.apk` to the tablet, open it, and allow
"install unknown apps" when prompted.

## Use

**Sound**
1. Turn on **Extra quiet volume**.
2. Set the normal volume to a low step (1 or 2, not zero).
3. Drag the **Extra reduction** slider right until it's comfortable.
   Each 10 dB sounds about half as loud.

**Screen**
1. Turn on **Extra dim screen**. The first time, Android opens the
   *Display over other apps* page: allow Night Mode, then press Back.
2. Set the tablet's own brightness to its lowest.
3. Drag the **Extra darkness** slider right. It stops at 80% because Android
   blocks taps through anything darker than that.

**Presets**
- One preset, *Bedtime*, is ready to use. Tap a preset to switch to it, or tap
  *All off*.
- Tap **+** to save your current settings as a new preset and give it a name.
- Press and hold a preset to **save your current settings** into it,
  **rename** it, or **delete** it.

**Quick Settings tiles**
Swipe down twice from the top, tap the pencil (edit) icon, and drag these into
your tiles:
- **Quiet sound**: turns extra-quiet volume on or off
- **Dim screen**: turns the dimmer on or off
- **Night preset**: each tap moves to the next preset, in the order shown in
  the app, then back to Off

Press and hold any of these tiles to open a small panel with the darkness and
volume sliders, on top of whatever app you're using.

**YouTube ads**
In the app's *YouTube ads* section, tap *Turn on in Accessibility settings*
and switch on **Night Mode – YouTube ads**. (On Android 13 and newer, if that
switch is greyed out: open the app's info page, tap ⋮ → *Allow restricted
settings*, and try again.) It then works on its own in the YouTube app:
- taps **Skip** as soon as YouTube shows it,
- mutes the sound and darkens the screen while any ad plays, then puts them
  back.

Each of the three can be switched off separately. It only looks at the YouTube
app (not YouTube in a browser). YouTube changes its app now and then, so ad
detection may occasionally need updating: the `NightMode` log shows what it
saw when an ad started.

A small notification shows while either feature is on. Tap *Adjust* there for
the slider panel, *Turn all off* to stop everything, or use the switches in
the app.

## If something doesn't work

- **Sound:** restart the app that's playing (YouTube, Spotify…) after turning
  the feature on. If another equalizer or sound app is active, it may compete.
- **Screen:** if an app refuses to work and mentions a "screen overlay"
  (common on install and permission screens), turn the dimmer off briefly.
- Screenshots won't show the dimming. That's normal.
- If a tile does nothing, Android may have blocked starting from the tile:
  the tile opens the app instead, and it works from there.
- After a reboot, open the app once (or tap a tile) to turn things back on.
