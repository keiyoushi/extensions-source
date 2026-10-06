package eu.kanade.tachiyomi.extension.vi.moetruyen

import android.util.Base64
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.Aegis128l
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.Aegis256
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.AesCbcHmac
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.AesGcmSiv
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.AesSiv
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.ChaCha20Poly1305
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.XChaCha20Poly1305
import eu.kanade.tachiyomi.extension.vi.moetruyen.cipher.Xsalsa20Poly1305
import keiyoushi.lib.ece.Ece
import keiyoushi.utils.parseAs
import keiyoushi.utils.readIntBigEndian
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ImgxCrypto {
    private val secureRandom = SecureRandom()
    private const val GOLDEN = 2654435769L

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    fun base64UrlEncode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun base64UrlDecode(value: String): ByteArray {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(padded, Base64.DEFAULT)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        if (aad.isNotEmpty()) updateAAD(aad)
        doFinal(ciphertext)
    }

    class EcdhKeyPair(
        private val privateKey: PrivateKey,
        private val publicKeyBytes: ByteArray,
    ) {
        val publicKey: String get() = base64UrlEncode(publicKeyBytes)

        fun deriveSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray {
            val peer = Ece.rawToPublicKey(peerPublicKeyBytes)
            return KeyAgreement.getInstance("ECDH").run {
                init(privateKey)
                doPhase(peer, true)
                generateSecret()
            }
        }
    }

    fun generateEcdhP256(): EcdhKeyPair {
        val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val point = Ece.publicKeyToRaw(keyPair.public as ECPublicKey)
        return EcdhKeyPair(keyPair.private, point)
    }

    fun channelAad(ownPublic: String, peerPublic: String, proof: String): ByteArray = """["imgx-reader-channel-v1","$ownPublic","$peerPublic","$proof"]""".toByteArray(Charsets.UTF_8)

    fun deriveChannelKey(sharedSecret: ByteArray, proof: String): ByteArray = Ece.hkdf(
        ikm = sharedSecret,
        salt = proof.toByteArray(Charsets.UTF_8),
        info = "imgx-reader-channel-v1".toByteArray(Charsets.UTF_8),
        length = 32,
    )

    fun openSealedPages(
        keyPair: EcdhKeyPair,
        sealed: SealedChannel,
        proof: String,
    ): List<ImgxPageAccess> {
        require(sealed.version == "imgx-reader-channel-v1") { "IMGX sealed page channel invalid" }
        val shared = keyPair.deriveSharedSecret(base64UrlDecode(sealed.publicKey))
        try {
            val plain = aesGcmDecrypt(
                deriveChannelKey(shared, proof),
                base64UrlDecode(sealed.iv),
                channelAad(keyPair.publicKey, sealed.publicKey, proof),
                base64UrlDecode(sealed.ciphertext),
            )
            val pages = String(plain, Charsets.UTF_8).parseAs<List<ImgxPageAccess>>()
            return pages.map { page ->
                val grant = page.grant ?: return@map page
                val ck = grant.channelKeys ?: return@map page
                val decrypted = openChannelKeys(keyPair, sealed, proof, shared, page, ck)
                ImgxPageAccess(
                    pageIndex = page.pageIndex,
                    storageKey = page.storageKey,
                    downloadUrl = page.downloadUrl,
                    width = page.width,
                    height = page.height,
                    grant = ImgxGrant(
                        version = grant.version,
                        algorithm = grant.algorithm,
                        imageId = grant.imageId,
                        issuedAt = grant.issuedAt,
                        expiresAt = grant.expiresAt,
                        nonce = grant.nonce,
                        keyNonce = grant.keyNonce,
                        signature = grant.signature,
                        wrappedDecodeKey = decrypted.wrappedDecodeKey ?: grant.wrappedDecodeKey,
                        wrappedContentKey = decrypted.wrappedContentKey ?: grant.wrappedContentKey,
                        wrappedV4Key = decrypted.wrappedV4Key ?: grant.wrappedV4Key,
                        decodeKey = decrypted.decodeKey ?: grant.decodeKey,
                        channelKeys = null,
                    ),
                )
            }
        } finally {
            shared.fill(0)
        }
    }

    private fun openChannelKeys(
        keyPair: EcdhKeyPair,
        sealed: SealedChannel,
        proof: String,
        shared: ByteArray,
        page: ImgxPageAccess,
        ck: ChannelKeys,
    ): DecryptedChannelKeys {
        require(ck.version == "IMGX-READER-PAGE-KEY-v1") { "IMGX page key version unsupported: ${ck.version}" }
        val pageKey = Ece.hkdf(
            shared,
            proof.toByteArray(Charsets.UTF_8),
            "IMGX-READER-PAGE-KEY-v1".toByteArray(Charsets.UTF_8),
            32,
        )
        try {
            val grant = page.grant!!
            val aad = """["${ck.version}",["${keyPair.publicKey}","${sealed.publicKey}","$proof"],${page.pageIndex},"${page.storageKey}","${grant.imageId.orEmpty()}",${grant.issuedAt},${grant.expiresAt},"${grant.nonce.orEmpty()}","${grant.signature.orEmpty()}"]"""
                .toByteArray(Charsets.UTF_8)
            val plain = aesGcmDecrypt(pageKey, base64UrlDecode(ck.iv), aad, base64UrlDecode(ck.ciphertext))
            return String(plain, Charsets.UTF_8).parseAs()
        } finally {
            pageKey.fill(0)
        }
    }

    fun openSealedCapability(
        keyPair: EcdhKeyPair,
        sealed: SealedChannel,
        proof: String,
    ): ReaderCapability {
        require(sealed.version == "imgx-reader-channel-v1") { "IMGX sealed channel invalid" }
        val shared = keyPair.deriveSharedSecret(base64UrlDecode(sealed.publicKey))
        try {
            val plain = aesGcmDecrypt(
                deriveChannelKey(shared, proof),
                base64UrlDecode(sealed.iv),
                channelAad(keyPair.publicKey, sealed.publicKey, proof),
                base64UrlDecode(sealed.ciphertext),
            )
            val list = String(plain, Charsets.UTF_8).parseAs<List<ReaderCapability>>()
            return list.firstOrNull()
                ?: throw IllegalStateException("IMGX capability empty")
        } finally {
            shared.fill(0)
        }
    }

    fun publicKeyHash(publicKeyB64Url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(base64UrlDecode(publicKeyB64Url))
        .joinToString("") { "%02x".format(it) }

    fun unwrapGrantKey(grant: ImgxGrant, storageKey: String, fieldName: String): ByteArray {
        val wrappedField = when (fieldName) {
            "wrappedV4Key" -> grant.wrappedV4Key
            "wrappedContentKey" -> grant.wrappedContentKey
            "wrappedDecodeKey" -> grant.wrappedDecodeKey
            else -> null
        } ?: throw IllegalStateException("IMGX $fieldName missing")
        val wrapped = base64UrlDecode(wrappedField)
        require(wrapped.size == 32) { "IMGX $fieldName invalid" }
        val grantString = listOf(
            "IMGX-GRANT-WRAP-v1",
            grant.version?.toString().orEmpty(),
            grant.algorithm.orEmpty(),
            grant.imageId.orEmpty(),
            grant.issuedAt?.toString().orEmpty(),
            grant.expiresAt?.toString().orEmpty(),
            grant.nonce.orEmpty(),
            grant.keyNonce.orEmpty(),
            grant.signature.orEmpty(),
            storageKey.trimStart('/'),
        ).joinToString(".")
        val wrapKey = deriveWrapKey(grantString, 32)
        for (i in wrapped.indices) {
            wrapped[i] = (wrapped[i].toInt() xor wrapKey[i].toInt()).toByte()
        }
        wrapKey.fill(0)
        return wrapped
    }

    private fun unwrapDecodeKey(grant: ImgxGrant, storageKey: String): ByteArray {
        if (grant.wrappedDecodeKey != null) {
            return unwrapGrantKey(grant, storageKey, "wrappedDecodeKey")
        }
        val raw = grant.decodeKey ?: throw IllegalStateException("IMGX decode key missing")
        return base64UrlDecode(raw)
    }

    private fun deriveWrapKey(input: String, length: Int): ByteArray {
        val output = ByteArray(length)
        var hash = fnv1a(input.toByteArray(Charsets.UTF_8))
        for (index in 0 until length) {
            if (index % 4 == 0) {
                hash = xorshift32(hash + index + GOLDEN)
            }
            output[index] = ((hash ushr ((index % 4) * 8)) and 0xFF).toInt().toByte()
        }
        return output
    }

    private fun fnv1a(bytes: ByteArray): Long {
        var hash = 2166136261L
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xFF)
            hash = (hash * 16777619L) and 0xFFFFFFFFL
        }
        return if (hash == 0L) GOLDEN else hash
    }

    private fun xorshift32(input: Long): Long {
        var value = input and 0xFFFFFFFFL
        value = value xor ((value shl 13) and 0xFFFFFFFFL)
        value = value xor (value ushr 17)
        value = value xor ((value shl 5) and 0xFFFFFFFFL)
        return value and 0xFFFFFFFFL
    }

    fun decodeProtectedPage(encrypted: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        require(encrypted.size > 13 && fourCc(encrypted, 0) == "IMGX") { "IMGX magic invalid" }
        return when (val version = encrypted[4].toInt()) {
            2 -> decodeImgxV2(encrypted, grant, storageKey)
            3 -> decodeImgxV3Path(encrypted, grant, storageKey)
            4 -> {
                val key = unwrapGrantKey(grant, storageKey, "wrappedV4Key")
                try {
                    decodeImgxV4(encrypted, key, grant.imageId.orEmpty(), storageKey)
                } finally {
                    key.fill(0)
                }
            }
            else -> throw IllegalStateException("IMGX version unsupported: $version")
        }
    }

    private fun decodeImgxV2(encrypted: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        val payload = encrypted.copyOfRange(13, encrypted.size)
        val key = unwrapDecodeKey(grant, storageKey)
        try {
            unshuffleBytes(payload, key)
            xorDecryptBytes(payload, key)
            return payload
        } finally {
            key.fill(0)
        }
    }

    private fun decodeImgxV3Path(encrypted: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        val intermediate = decodeImgxV3(encrypted, grant, storageKey)
        val imx4 = extractImx4FromWebp(intermediate)
        if (imx4 == null) return intermediate
        try {
            val key = unwrapGrantKey(grant, storageKey, "wrappedV4Key")
            try {
                return decodeImgxV4(imx4, key, grant.imageId.orEmpty(), storageKey)
            } finally {
                key.fill(0)
            }
        } finally {
            intermediate.fill(0)
        }
    }

    private fun decodeImgxV3(encrypted: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        require(encrypted.size > 41 && encrypted[4].toInt() == 3) { "IMGX v3 payload invalid" }
        val width = encrypted.readIntBigEndian(5)
        val height = encrypted.readIntBigEndian(9)
        require(width > 0 && height > 0) { "IMGX v3 dimensions invalid" }
        val key = unwrapGrantKey(grant, storageKey, "wrappedContentKey")
        try {
            val aad = listOf(
                "IMGX-v3",
                grant.imageId.orEmpty().trim(),
                storageKey.trimStart('/'),
                width.toString(),
                height.toString(),
            ).joinToString(".").toByteArray(Charsets.UTF_8)
            return aesGcmDecrypt(
                key,
                encrypted.copyOfRange(13, 25),
                aad,
                encrypted.copyOfRange(25, encrypted.size),
            )
        } finally {
            key.fill(0)
        }
    }

    private fun extractImx4FromWebp(bytes: ByteArray): ByteArray? {
        if (bytes.size < 12 || fourCc(bytes, 0) != "RIFF" || fourCc(bytes, 8) != "WEBP") return null
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (view.getInt(4).toLong() + 8 != bytes.size.toLong()) {
            throw IllegalStateException("IMGX WebP container invalid")
        }
        var payload: ByteArray? = null
        var chunks = 0
        var offset = 12
        while (offset < bytes.size) {
            if (++chunks > 1024 || offset + 8 > bytes.size) {
                throw IllegalStateException("IMGX WebP chunks invalid")
            }
            val size = view.getInt(offset + 4)
            val dataEnd = offset + 8 + size
            val paddedEnd = dataEnd + (size and 1)
            if (paddedEnd > bytes.size) {
                throw IllegalStateException("IMGX WebP chunk truncated")
            }
            if (fourCc(bytes, offset) == "IMX4") {
                if (
                    payload != null ||
                    size <= 78 ||
                    fourCc(bytes, offset + 8) != "IMGX" ||
                    bytes[offset + 12].toInt() != 4
                ) {
                    throw IllegalStateException("IMGX protected chunk invalid")
                }
                payload = bytes.copyOfRange(offset + 8, dataEnd)
            }
            offset = paddedEnd
        }
        return payload
    }

    private fun decodeImgxV4(
        payload: ByteArray,
        key: ByteArray,
        imageId: String,
        storageKey: String,
    ): ByteArray {
        require(
            payload.size > 78 &&
                fourCc(payload, 0) == "IMGX" &&
                payload[4].toInt() == 4,
        ) { "IMGX v4 file invalid" }
        val header = payload.copyOfRange(0, 78)
        val derived = Ece.hkdf(
            ikm = key,
            salt = header.copyOfRange(13, 45),
            info = "IMGX-v4.envelope".toByteArray(Charsets.UTF_8),
            length = 64,
        )
        val envelopeKey = derived.copyOfRange(0, 32)
        val contentKey = derived.copyOfRange(32, 64)
        try {
            val contextJson = """["IMGX-v4","$imageId","${storageKey.trimStart('/')}"]"""
                .toByteArray(Charsets.UTF_8)
            val envelope = aesGcmDecrypt(
                envelopeKey,
                header.copyOfRange(45, 57),
                header.copyOfRange(0, 57) + contextJson,
                header.copyOfRange(57, 78),
            )
            val profile = envelope[0].toInt()
            val body = payload.copyOfRange(78, payload.size)
            val contentAad = header + contextJson
            return when (profile) {
                1 -> aesGcmDecrypt(contentKey, body.copyOfRange(0, 12), contentAad, body.copyOfRange(12, body.size))
                2 -> {
                    // ChaCha20-Poly1305 IETF: nonce=body[0..12], ct+tag=body[12..]
                    ChaCha20Poly1305.decrypt(contentKey, body.copyOfRange(0, 12), body.copyOfRange(12, body.size), contentAad)
                }
                3 -> {
                    // XChaCha20-Poly1305 IETF: nonce=body[0..24], ct+tag=body[24..]
                    XChaCha20Poly1305.decrypt(contentKey, body.copyOfRange(0, 24), body.copyOfRange(24, body.size), contentAad)
                }
                4 -> {
                    // XSalsa20-Poly1305 secretbox: nonce(24)||mac(16)||ct; plain=sha256(aad)||image
                    Xsalsa20Poly1305.decrypt(contentKey, body, contentAad)
                }
                5 -> {
                    // secretstream xchacha20poly1305: header(24)+chunks of +17
                    val plainBytes = if (envelope.size >= 5) {
                        ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN).getInt(1)
                    } else {
                        body.size
                    }
                    XChaCha20Poly1305.decryptStream(contentKey, body, contentAad, plainBytes)
                }
                6 -> {
                    // AES-CBC + HMAC-SHA512; HKDF(contentKey, "IMGX-v4.p06") → 64B key
                    val p06Key = Ece.hkdf(
                        ikm = contentKey,
                        salt = ByteArray(32),
                        info = "IMGX-v4.p06".toByteArray(Charsets.UTF_8),
                        length = 64,
                    )
                    try {
                        AesCbcHmac.decrypt(p06Key, body, contentAad)
                    } finally {
                        p06Key.fill(0)
                    }
                }
                7 -> {
                    // AES-GCM-SIV: nonce=body[0..12], ciphertext+tag=body[12..]
                    AesGcmSiv.decrypt(contentKey, body.copyOfRange(0, 12), body.copyOfRange(12, body.size), contentAad)
                }
                8 -> {
                    // AES-SIV (RFC 5297): HKDF(contentKey, "IMGX-v4.p08") → 64B key; body=tag||ct
                    val sivKey = Ece.hkdf(
                        ikm = contentKey,
                        salt = ByteArray(32),
                        info = "IMGX-v4.p08".toByteArray(Charsets.UTF_8),
                        length = 64,
                    )
                    try {
                        AesSiv.decrypt(sivKey, body, contentAad)
                    } finally {
                        sivKey.fill(0)
                    }
                }
                9 -> {
                    // AEGIS-256: nonce=body[0..32], ciphertext+tag=body[32..]
                    Aegis256.decrypt(contentKey, body.copyOfRange(0, 32), body.copyOfRange(32, body.size), contentAad)
                }
                10 -> {
                    // AEGIS-128L: key=contentKey[0..16], nonce=body[0..16], ct+tag=body[16..]
                    Aegis128l.decrypt(
                        contentKey.copyOfRange(0, 16),
                        body.copyOfRange(0, 16),
                        body.copyOfRange(16, body.size),
                        contentAad,
                    )
                }
                else -> throw IllegalStateException("IMGX v4 profile unsupported: p0$profile")
            }
        } finally {
            envelopeKey.fill(0)
            contentKey.fill(0)
            derived.fill(0)
        }
    }

    private fun seedFromKey(key: ByteArray): Long {
        val seed = key.readIntBigEndian(0).toLong() and 0xFFFFFFFFL
        return if (seed == 0L) GOLDEN else seed
    }

    private fun unshuffleBytes(data: ByteArray, key: ByteArray) {
        val indices = IntArray(data.size)
        var seed = seedFromKey(key)
        for (i in data.size - 1 downTo 1) {
            seed = xorshift32(seed)
            indices[i] = (seed % (i + 1)).toInt()
        }
        for (i in 1 until data.size) {
            val j = indices[i]
            if (i != j) {
                val tmp = data[i]
                data[i] = data[j]
                data[j] = tmp
            }
        }
    }

    private fun xorDecryptBytes(data: ByteArray, key: ByteArray) {
        for (i in data.indices) {
            data[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String {
        if (offset + 4 > bytes.size) return ""
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }
}
