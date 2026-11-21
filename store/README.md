# Google Play listing assets

| Play Console field | File | Spec |
|---|---|---|
| App icon | `icon-512.png` | 512×512 PNG |
| Feature graphic | `feature-graphic.png` | 1024×500 PNG, no transparency |
| Android TV banner | `tv-banner.png` | 1280×720 PNG, no transparency |
| Android TV screenshots | `screenshots/*.png` | 1920×1080 PNG, in listing order |
| Privacy policy URL | `../docs/privacy-policy.md` | Served by GitHub Pages (see below) |

`src/` holds the SVG sources for the graphics. Re-render with ImageMagick, e.g.
`convert -background "#1B5E20" src/tv-banner.svg -alpha remove -alpha off tv-banner.png`.

Screenshots were taken on a clean 1080p Android TV emulator with fictional artists, albums and servers.

## Privacy policy hosting

Enable GitHub Pages for this repo (Settings → Pages → Deploy from branch → `main`, folder `/docs`).
The policy is then at `https://rickpoleshuck.github.io/tvmusic/privacy-policy`.

## Data safety form

- Does the app collect or share any required user data types? **No.**
  Server settings and credentials are stored on the device only, encrypted, and never sent to the developer.
- The app connects only to servers the user configures.

## Listing text

**App name:** TV Music

**Short description** (≤ 80 chars):
Play your own music on your TV from SFTP, SMB shares, USB drives and storage.

**Full description:**

TV Music plays the music you already own, right on your Android TV or Google TV, with no account and no subscription.

Connect your sources:
• SFTP servers, with password or SSH key login. Generate a key on the TV and scan its QR code to add it to your server.
• SMB / Windows / Samba shares (SMB2 and SMB3, user or guest).
• Music on the TV and attached USB drives, or any folder you choose.

Built for the remote:
• Browse folders with the D-pad, play an album, or shuffle everything at once.
• Shuffle all music across every source, or everything under one folder.
• Keeps playing in the background; your remote's play, pause and skip keys work.
• Seeking works on every source, and network files stream without downloading first.

Private by design: no ads, no analytics, no tracking. Your server settings and keys stay on your TV, encrypted.
