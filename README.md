# VeloVistrix

A fast, **read-only** gallery viewer for the photo library on your own
[PhotoPrism](https://www.photoprism.app/) server.

> VeloVistrix is an independent third-party client. It is not an official
> PhotoPrism app, and it is not affiliated with or endorsed by the PhotoPrism
> project.

## Install

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60">](https://play.google.com/store/apps/details?id=com.regnius.photoprism)

Or open the listing directly:
**[play.google.com/store/apps/details?id=com.regnius.photoprism](https://play.google.com/store/apps/details?id=com.regnius.photoprism)**

## What it does

- Sweep through your albums and your whole timeline in a grid that keeps up
- Open a photo full screen — pinch or double-tap to zoom, swipe for the next one
- See the capture details: date, camera, lens, exposure
- Browse images and videos as separate tabs inside an album
- Search your library and filter the results
- Open the favourites you starred on the server
- Play videos
- Run a slideshow — adjustable interval, optional Ken Burns effect, shuffle,
  and it starts from wherever you already are in the list

Your recent lists and thumbnails are cached, so the app still opens and shows
them when you have no connection.

## What it will not do

The app never sends a write request to your server. It cannot upload, edit,
delete, archive, re-tag, or re-index anything, and it offers no server
administration.

That is not a setting you have to trust — there is no code in the app that
writes to your library, so your originals are safe from it by construction.

## Privacy

- Your server address and session token are stored **encrypted, on your device
  only.** Neither ever leaves it.
- **No** analytics, **no** crash reporting, **no** ads, **no** tracking SDKs.
- The only host the app ever contacts is the PhotoPrism server you typed in.

Full policy: **[Privacy Policy](https://zephy-lee.github.io/velovistrix/privacy-policy.html)**

## Getting started

### 1. Enter your server address

Type your PhotoPrism address on the sign-in screen. The app takes whatever form
you have on hand and works out a reachable combination itself:

| You type | What it tries |
|---|---|
| `192.168.0.10` | the default port (2342), then the usual sub-paths (`/photoprism`, `/photo`, …) |
| `photos.example.com` | `https://` and `http://`, in an order that suits the address |
| `https://photos.example.com/library/browse` | a full URL pasted from your browser, as-is |

If it cannot connect, it shows you **every address it tried and why each one
failed** — so you learn whether it was the port, the path, or the certificate,
instead of a flat "connection failed".

Using a self-signed certificate? The app shows you the fingerprint and lets you
decide whether to trust it.

### 2. Sign in

Sign in with your password, or — better — with a PhotoPrism **app password**
(issue one under Settings → Account on your server). Giving a third-party client
a credential you can revoke at any time beats handing over your account password.

If your server runs in **public mode**, you go straight in with no sign-in at all.

### 3. Look around

The app opens on the **Albums** tab.

| Where | What you can do |
|---|---|
| Albums | Sort by name, reverse name, recently updated, or oldest; put favourites first |
| Inside an album | Switch between the **Images** and **Videos** tabs |
| Photos | Your whole timeline, scrolling as far as it goes |
| Favourites | Everything you have starred |
| Search (🔍) | Search the library, then narrow it with filters |

Tap any photo for the full-screen viewer:

- **Pinch** or **double-tap** to zoom
- **Swipe sideways** for the next or previous photo
- **Swipe down**, or press back, to close
- **Swipe up**, or tap the info icon, for capture details

The **⋮** menu starts a **slideshow** from the list you are looking at, at the
position you have scrolled to. Interval, Ken Burns and shuffle are in Settings,
which lives in the same menu. The screen stays awake while a slideshow runs.

## Requirements

- A PhotoPrism server you can reach, build **260601 (June 2026)** or newer
- **Android 12** or newer

## Support

Something broken, or missing? [Open an issue](https://github.com/zephy-lee/velovistrix/issues).

VeloVistrix is not made by the PhotoPrism team. Please report problems with the
app here rather than to the PhotoPrism project — they cannot help with it, and
it takes time away from the server everyone depends on.

Three things make a report much easier to act on:

- **The app version**, at the bottom of Settings
- **Your Android version**
- **Your PhotoPrism build**, shown under the server address when you sign in

If a photo or video fails to load, say whether it fails everywhere or only in
one place — the grid, the full-screen viewer, the slideshow. That single detail
usually points straight at the cause.

If the app is useful to you, you can support it through
[GitHub Sponsors](https://github.com/sponsors/zephy-lee).

## License

[GPL-3.0](LICENSE). VeloVistrix only calls PhotoPrism's HTTP API; it contains no
PhotoPrism source code.

Google Play and the Google Play logo are trademarks of Google LLC.
