package com.pluckcode.tvmusic.net

import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import java.security.SecureRandom

/** Generates an ed25519 key pair in OpenSSH format, entirely on the device. */
object KeyGen {
    data class KeyPair(val privatePem: String, val publicLine: String)

    fun ed25519(comment: String = "tvmusic@tv"): KeyPair {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val kp = gen.generateKeyPair()
        val priv = OpenSSHPrivateKeyUtil.encodePrivateKey(kp.private)
        val pub = OpenSSHPublicKeyUtil.encodePublicKey(kp.public)
        val body = Base64.encodeToString(priv, Base64.NO_WRAP).chunked(70).joinToString("\n")
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\n$body\n-----END OPENSSH PRIVATE KEY-----\n"
        return KeyPair(pem, "ssh-ed25519 ${Base64.encodeToString(pub, Base64.NO_WRAP)} $comment")
    }
}
