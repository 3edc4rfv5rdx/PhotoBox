# PhotoBox

**A gallery for the photos and videos on your phone that works on folders and
files, not on the system's media library.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/3edc4rfv5rdx/PhotoBox)](https://github.com/3edc4rfv5rdx/PhotoBox/releases/latest)

An album is a folder. PhotoBox finds them in the folders you tell it to
search — DCIM and Pictures to start with — on the phone and the SD card, and
shows what is really there, hidden folders included if you want them.

## Features

- **Albums**: one section per search folder, sorted by name, date or file count.
  Make, rename and delete albums; a folder of music is not taken for one.
- **Thumbnails**: a grid of 2 to 5 across (pinch or Settings), grouped by the
  day taken, with a fast-scroll bar, sorting and a filter by name and dates.
  Dates come from EXIF and are cached.
- **Viewer**: swipe, pinch and double-tap zoom; videos play in the built-in
  player or another app.
- **File actions**: share, rotate 90° without loss (EXIF only), rename, move
  or copy to any folder (with New folder), on one file or a long-tap
  selection. A RAW beside a photo always goes with it; nothing is
  overwritten.
- **Trash**: its own, one per storage in `Documents/PhotoBox/.Trash`, with
  restore, delete for good and auto-cleanup after 7 to 90 days.
- **Slideshow**: crossfades through the album, 1 to 30 seconds a photo, with
  pause, previous, next and info; picks up where it stopped. Its ⚙ shuffles
  the whole album, each photo once, repeats it round and round, writes
  each photo's date and name in its corner, sets the time per photo, and
  slowly zooms and drifts each photo a random way.
- **Info**: size, resolution, date taken, camera settings and the place on a map.
- **Rotation**: photos, videos and the slideshow turn with the sensor even
  with auto-rotate off, while the app stays upright — or not, as set.
- **Other apps**: open a photo or video with PhotoBox to browse its whole
  folder, or pick files from PhotoBox when an app asks for one.
- **TV boxes**: in the Android TV launcher, and works with a remote or an air mouse.
- English, Russian and Ukrainian; light and dark themes with accent colours.

PhotoBox needs "All files access": it reads folders directly, not through the
media library.

## Install

Download the APK from the [latest release](https://github.com/3edc4rfv5rdx/PhotoBox/releases/latest):
`photobox-<version>-arm64-v8a.apk` runs on almost every modern phone;
`photobox-<version>-armeabi-v7a.apk` is for a 32-bit one or a TV box.
