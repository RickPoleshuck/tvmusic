---
title: TV Music – Privacy Policy
---

# TV Music – Privacy Policy

_Effective November 20, 2026_

TV Music is a music player for Android TV and Google TV. It plays music from servers and storage that you set up yourself. This policy explains what the app does with your information. In short: **TV Music does not collect, share or sell any personal data.**

## Information we collect

None. TV Music has no user accounts, advertising, analytics, crash reporting or tracking of any kind. The developer receives no information about you, your device, your music or how you use the app.

## Information stored on your device

To connect to your music, the app saves the sources you add. Depending on the source type, that can include:

- server addresses, ports, share names and folders
- usernames, passwords, SSH private and public keys and key passphrases
- the fingerprint of each SFTP server's host key, used to detect if a server changes
- folders you grant access to with Android's folder picker

These settings are stored only on your TV. They are encrypted with a key held in the Android Keystore, which never leaves the device. The app does not include them in Android backups. SSH keys generated in the app are created on the TV and are never sent anywhere.

## Network connections

TV Music connects only to the SFTP and SMB servers you configure, to list folders and stream music. That traffic goes directly between your TV and your servers and is never routed through, or copied to, the developer or any third party. SFTP connections are always encrypted. SMB connections are protected according to your server's settings.

## Permissions

- **Music and audio** (or **storage** on older Android versions): to find and play music saved on the TV and on attached USB drives.
- **Internet and network state**: to connect to your SFTP and SMB servers.
- **Foreground service and wake lock**: to keep music playing in the background and respond to your remote's media keys.

## Deleting your data

Deleting a source in the app removes its saved settings. Uninstalling TV Music removes all of its data from the device.

## Children

TV Music does not collect personal information from anyone, including children.

## Changes to this policy

If this policy changes, the updated version will be posted on this page with a new effective date.

## Contact

Questions about this policy can be raised at <https://github.com/RickPoleshuck/tvmusic/issues>.
