# PhotoBox

**A gallery for the photos and videos on your phone that works on folders and
files, not on the system's media library.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Work in progress, built to `TZ.md`.

## Features

- **Albums**: every folder with photos or videos directly in it, looked for
  in the search folders — DCIM and Pictures until changed — one section each.
  **+** → Add search folder adds one; ⋮ on a section's heading takes it out
  of the search, the files untouched. With none left, the whole phone and SD
  card are searched. A folder with music in it is no album: its pictures are
  covers. Each album shows its newest file as the cover, the number of files
  and where it is; sorted by name, newest first or most files first. Folders
  that start with a dot or hold a `.nomedia` file — and everything under
  them — are listed only with "Show hidden folders" on in Settings. Tapping
  the album's name at the top opens the list. **+** → New album makes one, in
  the first search folder or any folder picked; it shows in the list once
  something is put in it.
- **Thumbnails**: the open album as a grid, 2 to 5 across — pinch to change,
  and the choice is kept. Newest first under a heading per day, or by name or
  size from the ⋮ menu. A long album gets a bar on the right edge to drag
  through it, showing the month, letter or size it has reached. The date is
  when the photo or video was taken, from its EXIF or container, else the
  file's own time; it is read once and cached.
- **Viewer**: a tap on a thumbnail opens it full screen, in the grid's order.
  Swipe to the next or previous file; pinch or double-tap to zoom, and drag
  while zoomed. A tap hides or shows the bar on top. A video shows a still
  with a play button: it plays in PhotoBox's own player — play and pause, the
  time and a seek bar, hidden and shown by a tap — or in another app with
  "Video player" set to that in Settings.
- **Trash**: the viewer's ⋮ → To trash moves the file — and a RAW beside it
  under the same name — into a hidden `.PhotoTrash` folder in its own folder,
  which other galleries pass by; the viewer moves on to the next one. ⋮ →
  Trash lists everything in the trashes of the search folders, with where it
  goes back to and when it was deleted. A tap restores a file or deletes it
  for good; the bar on top restores or deletes everything. A file is deleted
  for good once it has been in the trash longer than set in Settings — 30
  days unless changed — checked at each start.
- **Info**: the viewer's ⋮ menu and the slideshow's (i) show a file's name,
  folder, size, picture size, length of a video, when it was taken and last
  changed, the camera and its settings, and where it was taken — a tap on
  that opens it in a map app.
- **Turning**: the app stays in the device's own orientation — portrait on a
  phone, landscape on a TV box — and photos, videos and the slideshow
  turn with the phone by its sensor, whatever the system's auto-rotate says;
  with the phone on its side, a swipe still runs along the photo. Settings
  can make them turn only while the system's auto-rotate is on, or never.
- **Slideshow**: ⋮ → Slideshow goes through the album's photos in the grid's
  order, round and round, each fading into the next; videos are passed over.
  Each photo stays up for the time set in Settings with a slider, 1 to 30
  seconds, 5 unless changed.
  The screen stays on and the system bars are hidden. A tap shows the
  controls — back at the top; previous, info, pause or go on and next in a
  block at the bottom — which hide again after
  a few seconds; Back stops it too. After a pause it moves on 3 seconds after
  Go on. Started again in the album the last slideshow ran in, it asks
  whether to go on from the photo it stopped on or begin from the start.
- **Folder access**: PhotoBox reads files directly and needs "All files access".

## Install

Download the APK from the [latest release](https://github.com/3edc4rfv5rdx/PhotoBox/releases/latest):
`photobox-<version>-arm64-v8a.apk` runs on almost every modern phone;
`photobox-<version>-armeabi-v7a.apk` is for a 32-bit one or a TV box.
