package app.tvmusic

import android.app.Application
import app.tvmusic.data.SourceStore
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
    }
}
