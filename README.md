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
- **Folder access**: PhotoBox reads files directly and needs "All files access".

## Install

Download the APK from the [latest release](https://github.com/3edc4rfv5rdx/PhotoBox/releases/latest):
`photobox-<version>-arm64-v8a.apk` runs on almost every modern phone.
