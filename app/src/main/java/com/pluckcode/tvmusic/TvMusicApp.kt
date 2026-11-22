package com.pluckcode.tvmusic

import android.app.Application
import com.pluckcode.tvmusic.data.PlayHistory
import com.pluckcode.tvmusic.data.SourceStore
import com.pluckcode.tvmusic.data.TrackCache
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class TvMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android ships a stripped-down "BC" provider; replace it with the full one so
        // sshj can handle ed25519 / OpenSSH keys. Added last so AndroidKeyStore stays preferred.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        SourceStore.init(this)
        PlayHistory.init(this)
        TrackCache.init(this)
    }
}
