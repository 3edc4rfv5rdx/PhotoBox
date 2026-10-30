# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- N: Trash: the viewer's ⋮ sends a file, RAW and all, to a hidden trash in its own folder; the trash screen restores or deletes for good, one by one or all, and keeps files for the days set in Settings
- N: Albums are looked for in search folders — DCIM and Pictures to start with — listed as sections that + adds to and ⋮ takes out; a folder with music in it is no album
- N: Info on a file — name, folder, size, picture size, dates, camera and its settings, place on a map — from the viewer's ⋮ menu and the slideshow's controls
- N: The slideshow has controls shown by a tap — back at the top, and previous, info, pause and next in one block at the bottom — and can go on from the photo the last slideshow stopped on, when it was in the same album
- F: The slideshow's time per photo is a slider from 1 to 30 seconds
- F: Photos and videos turn by the sensor always, only like the system's auto-rotate, or never — a choice in Settings instead of a switch
- N: Photos, videos and the slideshow turn with the phone by the sensor, auto-rotate off or not, while the rest of the app stays in the device's own orientation — portrait on a phone, landscape on a TV box; the video player has its own upright controls for it
- N: Slideshow of the album's photos from the ⋮ menu, faded one into the next, each held for the time set in Settings
- N: Videos play in the app's own player, or in another app with the Video player setting
- N: A tap on a thumbnail opens the file full screen: swipe to the next one, pinch or double-tap to zoom, tap to hide the bar; a video opens in the phone's player
- R: Top-bar icon buttons, progress screens and bitmap caches each come from one shared helper
- I: Release builds include armeabi-v7a for 32-bit phones and TV boxes
- N: The album opens as a grid of thumbnails, 2 to 5 across by pinch, with day headings, a fast-scroll bar and sorting by date, name or size; dates taken are read from EXIF once and cached
- N: Albums — every folder with photos or videos, with cover, count and path, sorted by name, date or number of files, hidden ones behind a switch in Settings; + makes a new one
- I: Project skeleton: build scripts from the template, updater and About from ../shared, settings for theme, colour, language and update check, the ⋮ menu and ✕ exit
