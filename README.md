# TV Music

An Android TV / Google TV app (Chromecast with Google TV, Google TV sets, Android TV boxes) that plays music from:

- **SFTP** – password **or public/private key** login (OpenSSH, PEM/PKCS#8 and PuTTY keys; encrypted keys with passphrase)
- **SMB** – SMB2/SMB3 Windows/Samba shares (user/password or guest)
- **Local files** – "Music on this device" (internal storage + USB drives, via Android's media index) or any folder picked with the system folder picker

Playback runs in a media session service, so music keeps playing in the background, the remote's
play/pause/next keys work, and seeking works on every source (SFTP/SMB files are streamed with random access, not downloaded).

## SSH keys on a TV

In the SFTP source editor choose **Login with: Public/private key**, then either:

1. **Generate new key on TV** – creates an ed25519 key pair on the device and shows the public key as text and a **QR code**.
   Scan it with your phone and append it to `~/.ssh/authorized_keys` on the server.
2. **Import key file** – pick a private key (or public key) file from USB/storage (needs a file picker on the TV).
3. Paste the key into the *Private key* field (easiest with the Google TV phone app's keyboard, or `adb shell input text`).

The server's host key is trusted on first connect and pinned afterwards; if it changes the connection is refused
until you press **Forget host key**. All source settings, including passwords and private keys, are stored encrypted
with a key held in the Android Keystore.

## Build & install

Requires JDK 17+ and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug
adb connect <tv-ip>:5555      # enable Developer options → USB/Network debugging on the TV first
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app appears in the TV launcher as **TV Music**.

## Layout

| Path | What |
|---|---|
| `net/Connections.kt` | Pooled SFTP (sshj) and SMB (smbj) connections, key auth, TOFU host keys |
| `net/KeyGen.kt` | On-device ed25519 key generation (OpenSSH format) |
| `browse/Browsers.kt` | Folder listing for SFTP, SMB, SAF folders and the MediaStore library |
| `playback/DataSources.kt` | ExoPlayer data sources that stream `sftp://` and `smb://` with seeking |
| `playback/PlaybackService.kt` | Media3 `MediaSessionService` hosting ExoPlayer |
| `ui/` | Jetpack Compose UI built for D-pad navigation |
