# Night Mode

A tiny, free, ad-free Android app that makes your phone or tablet **quieter
than its lowest volume step** and **darker than its lowest brightness**. It
also skips, mutes and darkens YouTube ads. No internet, no data collected.

## Install

1. On the phone or tablet, download **NightMode.apk** from the
   [latest release](https://github.com/Yscura/Nightmode/releases/latest).
2. Open it. Allow installing apps from your browser or Files app when asked.
3. If Play Protect warns about an unknown app, tap **More details → Install
   anyway**. This is normal for apps not from the Play Store.

Needs Android 9 or newer.

## Use

- **Sound:** set the normal volume low (not zero), turn on **Extra quiet
  volume**, then drag the slider until it's comfortable.
- **Screen:** set brightness to its lowest, turn on **Extra dim screen**
  (allow *Display over other apps* the first time), then drag the slider.
- **Presets:** tap one to use it. Tap **+** to save your current settings as a
  new preset. Press and hold to update, rename or delete it.
- **Quick Settings tiles:** swipe down twice, tap the pencil, and add *Quiet
  sound*, *Dim screen* and *Night preset*. Press and hold a tile for quick
  sliders over any app. The notification's **Adjust** button opens them too.
- **YouTube ads:** in the app, tap *Turn on in Accessibility settings* and
  switch on **Night Mode – YouTube ads**. If that switch is greyed out
  (Android 13+), open the app's info page, tap **⋮ → Allow restricted
  settings**, and try again. Works in the YouTube app only, not in a browser.

## If something doesn't work

- **No sound change:** restart the app that's playing (YouTube, Spotify…).
- **An app complains about a "screen overlay":** turn the dimmer off briefly.
- **After a reboot:** open the app or tap a tile to turn things back on.
- **Ads not detected:** YouTube changes its app now and then. Detection may
  need updating (the `NightMode` log shows what it saw).

## For developers

Open the folder in Android Studio, or build from the command line:

```
gradlew assembleRelease
```

Use JDK 17–21. Gradle 8.9 can't run on newer Java. Release signing reads
`keystore.properties` and `release.jks` from the project folder (both kept out
of git). Without them, use `gradlew assembleDebug`.
