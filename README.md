# PhotoBox

**A gallery for the photos and videos on your phone that works on folders and
files, not on the system's media library.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Work in progress, built to `TZ.md`.

## Features

- **Albums**: every folder on the phone or the SD card with photos or videos
  directly in it, with the newest one as its cover, the number of files and
  where it is. Sorted by name, newest first or most files first. Folders that
  start with a dot or hold a `.nomedia` file — and everything under them — are
  listed only with "Show hidden folders" on in Settings. Tapping the album's
  name at the top opens the list. **+** makes a new album, in `Pictures` or any
  folder picked; it shows in the list once something is put in it.
- **Thumbnails**: the open album as a grid, 2 to 5 across — pinch to change,
  and the choice is kept. Newest first under a heading per day, or by name or
  size from the ⋮ menu. A long album gets a bar on the right edge to drag
  through it, showing the month, letter or size it has reached. The date is
  when the photo or video was taken, from its EXIF or container, else the
  file's own time; it is read once and cached.
- **Folder access**: PhotoBox reads files directly and needs "All files access".

## Install

Download the APK from the [latest release](https://github.com/3edc4rfv5rdx/PhotoBox/releases/latest):
`photobox-<version>-arm64-v8a.apk` runs on almost every modern phone;
`photobox-<version>-armeabi-v7a.apk` is for a 32-bit one or a TV box.
