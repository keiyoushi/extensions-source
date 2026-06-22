package eu.kanade.tachiyomi.extension.vi.moetruyen

import android.util.Base64
import keiyoushi.utils.parseAs
import keiyoushi.utils.readIntBigEndian
import keiyoushi.utils.readUShortBigEndian
import kotlinx.serialization.Serializable
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ImageDecryptor {

    fun decrypt(imgxData: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        require(imgxData.size > 13) { "IMGX payload empty" }
        require(
            imgxData[0] == 0x49.toByte() && imgxData[1] == 0x4D.toByte() &&
                imgxData[2] == 0x47.toByte() && imgxData[3] == 0x58.toByte(),
        ) { "IMGX magic invalid" }

        return when (val version = imgxData[4].toInt()) {
            2 -> decryptV2(imgxData, grant, storageKey)
            3 -> decryptV3(imgxData, grant, storageKey)
            else -> throw IllegalArgumentException("Unsupported IMGX version: $version")
        }
    }

    // ========================== v2 ==========================

    private fun decryptV2(imgxData: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        val payload = imgxData.copyOfRange(13, imgxData.size)
        val key = unwrapKey(grant, storageKey, grantSalt = null)

        unshuffle(payload, key)
        xorDecrypt(payload, key)

        return payload
    }

    // ========================== v3 ==========================

    private fun decryptV3(imgxData: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        if (grant.wrappedContentKey != null) {
            return decryptV3AesGcm(imgxData, grant, storageKey)
        }

        val headerSize = imgxData.readUShortBigEndian(6)
        val headerBytes = imgxData.copyOfRange(8, 8 + headerSize)
        val header = String(headerBytes, Charsets.UTF_8).parseAs<ImgxV3Header>()

        val payload = imgxData.copyOfRange(8 + headerSize, imgxData.size)
        val key = unwrapKey(grant, storageKey, grantSalt = header.grantSalt)

        if (header.blockSize != null && header.blockSize > 0) {
            blockCipherDecrypt(payload, key, header.blockSize)
        }
        unshuffle(payload, key)
        xorDecrypt(payload, key)

        return payload
    }

    private fun decryptV3AesGcm(imgxData: ByteArray, grant: ImgxGrant, storageKey: String): ByteArray {
        require(imgxData.size > 41) { "IMGX v3 payload empty" }

        val width = imgxData.readIntBigEndian(5)
        val height = imgxData.readIntBigEndian(9)
        require(width > 0 && height > 0) { "IMGX dimensions invalid" }

        val key = unwrapContentKey(grant, storageKey)
        val iv = imgxData.copyOfRange(13, 25)
        val payload = imgxData.copyOfRange(25, imgxData.size)
        val aad = buildV3AdditionalData(grant, storageKey, width, height)

        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(aad)
            doFinal(payload)
        }
    }

    private fun blockCipherDecrypt(data: ByteArray, key: ByteArray, blockSize: Int) {
        val numBlocks = (data.size + blockSize - 1) / blockSize
        var seed = seedFromKey(key)

        val seeds = UIntArray(numBlocks)
        for (block in 0 until numBlocks) {
            seed = xorshift32(seed)
            seeds[block] = seed
        }

        for (block in numBlocks - 1 downTo 0) {
            val start = block * blockSize
            val end = minOf(start + blockSize, data.size)
            val blockLen = end - start
            val blockSeed = seeds[block]

            val blockKey = (blockSeed and 0xFFu).toInt().toByte()
            val rotateAmount = ((blockSeed shr 8).toInt() % blockLen)

            val temp = ByteArray(blockLen)
            for (i in 0 until blockLen) {
                temp[i] = (data[start + i].toInt() xor blockKey.toInt()).toByte()
            }

            for (i in 0 until blockLen) {
                data[start + i] = temp[((i - rotateAmount) % blockLen + blockLen) % blockLen]
            }
        }
    }

    // ========================== shared ==========================

    private fun unwrapKey(grant: ImgxGrant, storageKey: String, grantSalt: String?): ByteArray {
        if (grant.wrappedDecodeKey != null) {
            val wrapped = base64UrlDecode(grant.wrappedDecodeKey)
            require(wrapped.size == 32) { "IMGX wrapped grant invalid" }
            val grantString = buildGrantString(grant, storageKey, grantSalt)
            val unwrapKey = deriveKeyFromString(grantString, 32)
            for (i in wrapped.indices) {
                wrapped[i] = (wrapped[i].toInt() xor unwrapKey[i].toInt()).toByte()
            }
            return wrapped
        }
        return base64UrlDecode(grant.decodeKey!!)
    }

    private fun unwrapContentKey(grant: ImgxGrant, storageKey: String): ByteArray {
        val wrapped = base64UrlDecode(grant.wrappedContentKey!!)
        require(wrapped.size == 32) { "IMGX wrapped content grant invalid" }
        val grantString = buildGrantString(grant, storageKey, grantSalt = null)
        val unwrapKey = deriveKeyFromString(grantString, 32)
        for (i in wrapped.indices) {
            wrapped[i] = (wrapped[i].toInt() xor unwrapKey[i].toInt()).toByte()
        }
        return wrapped
    }

    private fun buildGrantString(grant: ImgxGrant, storageKey: String, grantSalt: String?): String {
        val stripped = storageKey.trimStart('/')
        val parts = mutableListOf(
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
        )
        if (!grantSalt.isNullOrEmpty()) {
            parts.add(grantSalt)
        }
        return parts.joinToString(".")
    }

    private fun buildV3AdditionalData(grant: ImgxGrant, storageKey: String, width: Int, height: Int): ByteArray {
        val imageId = grant.imageId?.trim().orEmpty()
        require(imageId.isNotEmpty()) { "IMGX v3 image id missing" }

        return listOf(
            "IMGX-v3",
            imageId,
            storageKey.trimStart('/'),
            width.toString(),
            height.toString(),
        ).joinToString(".")
            .toByteArray(Charsets.UTF_8)
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
        val seed = key.readIntBigEndian(0).toUInt()
        return if (seed == 0u) GOLDEN_RATIO else seed
    }

    private fun base64UrlDecode(input: String): ByteArray {
        val base64 = input.replace('-', '+').replace('_', '/')
        return Base64.decode(base64, Base64.DEFAULT)
    }

    private val GOLDEN_RATIO = 2654435769u

    @Serializable
    private class ImgxV3Header(
        val blockSize: Int? = null,
        val grantSalt: String? = null,
    )
}
