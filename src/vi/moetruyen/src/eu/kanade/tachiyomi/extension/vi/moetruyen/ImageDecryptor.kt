package eu.kanade.tachiyomi.extension.vi.moetruyen

import android.util.Base64

object ImageDecryptor {

    fun decrypt(imgxData: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        require(imgxData.size > 13) { "IMGX payload empty" }
        require(
            imgxData[0] == 0x49.toByte() && imgxData[1] == 0x4D.toByte() &&
                imgxData[2] == 0x47.toByte() && imgxData[3] == 0x58.toByte(),
        ) { "IMGX magic invalid" }
        require(imgxData[4] == 2.toByte()) { "IMGX version invalid" }

        val payload = imgxData.copyOfRange(13, imgxData.size)
        val key = unwrapKey(grant, storageKey)

        unshuffle(payload, key)
        xorDecrypt(payload, key)

        return payload
    }

    private fun unwrapKey(grant: ImgxGrant, storageKey: String): ByteArray {
        if (grant.wrappedDecodeKey != null) {
            val wrapped = base64UrlDecode(grant.wrappedDecodeKey)
            require(wrapped.size == 32) { "IMGX wrapped grant invalid" }
            val grantString = buildGrantString(grant, storageKey)
            val unwrapKey = deriveKeyFromString(grantString, 32)
            for (i in wrapped.indices) {
                wrapped[i] = (wrapped[i].toInt() xor unwrapKey[i].toInt()).toByte()
            }
            return wrapped
        }
        return base64UrlDecode(grant.decodeKey!!)
    }

    private fun buildGrantString(grant: ImgxGrant, storageKey: String): String {
        val stripped = storageKey.trimStart('/')
        return listOf(
            "IMGX-GRANT-WRAP-v1",
            grant.version?.toString().orEmpty(),
            grant.algorithm.orEmpty(),
            grant.imageId.orEmpty(),
            grant.issuedAt?.toString().orEmpty(),
            grant.expiresAt?.toString().orEmpty(),
            grant.nonce.orEmpty(),
            grant.keyNonce.orEmpty(),
            grant.signature.orEmpty(),
            stripped,
        ).joinToString(".")
    }

    private fun deriveKeyFromString(input: String, length: Int): ByteArray {
        val key = ByteArray(length)
        var hash = fnv1a(input.toByteArray(Charsets.UTF_8))
        for (i in 0 until length) {
            if (i % 4 == 0) {
                hash = xorshift32(hash + i.toUInt() + GOLDEN_RATIO)
            }
            key[i] = (hash.toInt() ushr (i % 4 * 8) and 0xFF).toByte()
        }
        return key
    }

    private fun fnv1a(data: ByteArray): UInt {
        var hash = 2166136261u
        for (b in data) {
            hash = hash xor b.toUByte().toUInt()
            hash = (hash.toLong() * 16777619L).toUInt()
        }
        return if (hash == 0u) GOLDEN_RATIO else hash
    }

    private fun xorshift32(input: UInt): UInt {
        var t = input
        t = t xor (t shl 13)
        t = t xor (t shr 17)
        t = t xor (t shl 5)
        return t
    }

    private fun unshuffle(data: ByteArray, key: ByteArray) {
        val indices = IntArray(data.size)
        var seed = seedFromKey(key)
        for (i in data.size - 1 downTo 1) {
            seed = xorshift32(seed)
            indices[i] = (seed.toLong() % (i + 1)).toInt()
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

    private fun xorDecrypt(data: ByteArray, key: ByteArray) {
        for (i in data.indices) {
            data[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
    }

    private fun seedFromKey(key: ByteArray): UInt {
        require(key.size >= 4) { "IMGX key invalid" }
        val seed = (key[0].toUByte().toUInt() shl 24) or
            (key[1].toUByte().toUInt() shl 16) or
            (key[2].toUByte().toUInt() shl 8) or
            key[3].toUByte().toUInt()
        return if (seed == 0u) GOLDEN_RATIO else seed
    }

    private fun base64UrlDecode(input: String): ByteArray {
        val base64 = input.replace('-', '+').replace('_', '/')
        return Base64.decode(base64, Base64.DEFAULT)
    }

    private val GOLDEN_RATIO = 2654435769u
}
