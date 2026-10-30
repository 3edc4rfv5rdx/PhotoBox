# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- F: Moving and copying show their progress by bytes, with the sizes, so a large video copied to the SD card moves the bar
- E: Renaming the photo a slideshow stopped on no longer makes the next one start over
- F: A file opened from another app shows how many dates of its folder are left to read, the way an album does
- F: Settings set the thumbnails across too, for a TV remote that cannot pinch
- E: A move onto another storage that copies a file but cannot delete the original now says so instead of leaving it in both places quietly
- F: An empty album opened offers Delete album right where it says it is empty
- E: A zoomed photo comes back unzoomed once it is rotated
- E: A file opened from the trash shows its own name and folder in the bar and in Info, not the name it is kept under
- F: The video player answers a remote: ← and → jump 10 seconds, OK plays or pauses
- N: TV boxes: an Android TV launcher banner, a focus frame on thumbnails, and remote arrows and OK in the viewer and the slideshow
- N: Other apps can pick photos and videos through PhotoBox, one by a tap or several with a long tap and ✓
- N: Open with PhotoBox: a photo or video from another app shows full screen among the rest of its folder
- N: Filter the album by part of the name and a range of days, shown in a line under the top bar; the viewer and the slideshow follow it
- N: Rotate photos 90° clockwise without loss, by their EXIF orientation, from the viewer's ⋮ or the picked files'
- F: The slideshow stops after the last photo with the app's icon over it instead of going round, and a show seen to the end is not offered to go on
- N: Rename an album from its ⋮ in the album list; the open album and the slideshow's place follow it
- N: Rename a file from the viewer's ⋮ or the picked one's, its RAW along with it, the extension kept
- F: An empty album's ⋮ in the album list deletes its folder
- F: An empty folder shows in the album list as an empty album with the app's icon for a cover, and each search folder's ⋮ makes a new album in it
- F: The album's name on the main screen is shown in brackets, [ Camera ]
- N: Move or copy picked files, or the one in the viewer, into a folder chosen in the app's folder list, which can now make a new folder; a taken name gets a number, a RAW goes with its photo, and moving, copying or trashing many files shows progress
- N: A long tap picks thumbnails, a tap then picks more; the picked files are shared or moved to the trash together, and the viewer's ⋮ shares a single file
- F: One trash per storage, Documents/PhotoBox/.Trash, instead of one in every folder — what was in the old ones is carried over; the trash screen shows its totals, opens files full screen and counts restoring or emptying it all
- F: The open album stands out in the album list filled with the accent colour, and the list opens scrolled to it
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
