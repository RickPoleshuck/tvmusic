---
title: TV Music – Privacy Policy
---

# TV Music – Privacy Policy

_Effective November 21, 2026_

TV Music is a music player for Android TV and Google TV, published by PluckCode LLC ("PluckCode", "we" or "us"). It plays music from servers and storage that you set up yourself. This policy explains what the app does with your information. In short: **TV Music does not collect, share or sell any personal data.**

## Information we collect

None. TV Music has no user accounts, advertising, analytics, crash reporting or tracking of any kind. PluckCode receives no information about you, your device, your music or how you use the app.

## Information stored on your device

To connect to your music, the app saves the sources you add. Depending on the source type, that can include:

- server addresses, ports, share names and folders
- usernames, passwords, SSH private and public keys and key passphrases
- the fingerprint of each SFTP server's host key, used to detect if a server changes
- folders you grant access to with Android's folder picker
- a play history: which tracks have been played and when, so shuffle can play every track once before repeating any

This information is stored only on your TV. Source settings are encrypted with a key held in the Android Keystore, which never leaves the device. None of the app's data is included in Android backups. SSH keys generated in the app are created on the TV and are never sent anywhere.

## Network connections

TV Music connects only to the SFTP and SMB servers you configure, to list folders and stream music. That traffic goes directly between your TV and your servers and is never routed through, or copied to, PluckCode or any third party. SFTP connections are always encrypted. SMB connections are protected according to your server's settings.

## Permissions

- **Music and audio** (or **storage** on older Android versions): to find and play music saved on the TV and on attached USB drives.
- **Internet and network state**: to connect to your SFTP and SMB servers.
- **Foreground service and wake lock**: to keep music playing in the background and respond to your remote's media keys.

## Deleting your data

Deleting a source in the app removes its saved settings. Choosing **Refresh** while browsing clears the play history. Uninstalling TV Music removes all of its data from the device.

## Children

TV Music does not collect personal information from anyone, including children.

## Changes to this policy

If this policy changes, the updated version will be posted on this page with a new effective date.

## Contact

Questions about this policy or your privacy can be sent to:

PluckCode LLC  
Email: <info@pluckcode.com>

You can also raise questions at <https://github.com/RickPoleshuck/tvmusic/issues>.
