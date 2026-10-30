# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- N: Videos play in the app's own player, or in another app with the Video player setting
- N: A tap on a thumbnail opens the file full screen: swipe to the next one, pinch or double-tap to zoom, tap to hide the bar; a video opens in the phone's player
- R: Top-bar icon buttons, progress screens and bitmap caches each come from one shared helper
- I: Release builds include armeabi-v7a for 32-bit phones and TV boxes
- N: The album opens as a grid of thumbnails, 2 to 5 across by pinch, with day headings, a fast-scroll bar and sorting by date, name or size; dates taken are read from EXIF once and cached
- N: Albums — every folder with photos or videos, with cover, count and path, sorted by name, date or number of files, hidden ones behind a switch in Settings; + makes a new one
- I: Project skeleton: build scripts from the template, updater and About from ../shared, settings for theme, colour, language and update check, the ⋮ menu and ✕ exit
