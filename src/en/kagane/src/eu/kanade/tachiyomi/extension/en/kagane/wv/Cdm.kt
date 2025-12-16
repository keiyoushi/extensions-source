package eu.kanade.tachiyomi.extension.en.kagane.wv

import eu.kanade.tachiyomi.extension.en.kagane.toBase64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import kotlin.random.Random
import kotlin.random.nextUInt

enum class DeviceTypes(val value: Int) {
    CHROME(1),
    ANDROID(2),
    ;

    companion object {
        fun fromValue(value: Int): DeviceTypes? = values().find { it.value == value }
    }
}

@Serializable
class V2(
    val version: UByte = 2u,
    val type: DeviceTypes,
    val privateKey: ByteArray,
    val clientId: ByteArray,
) {
    companion object {
        private const val MAGIC = "WVD".length

        @Suppress("UNUSED_VARIABLE", "unused")
        fun parseStream(stream: ByteArray): V2 {
            val buffer = ByteBuffer.wrap(stream).order(ByteOrder.BIG_ENDIAN)

            val signature = ByteArray(MAGIC)
            buffer.get(signature)

            val version = buffer.get().toUByte()
            require(version == 2.toUByte()) { "Unsupported version: $version" }

            val typeValue = buffer.get().toInt()
            val type = DeviceTypes.fromValue(typeValue)
                ?: throw IllegalArgumentException("Invalid device type: $typeValue")

            val securityLevel = buffer.get().toUByte()
            val flags = buffer.get().toUByte().takeIf { it != 0u.toUByte() }

            val privateKeyLen = buffer.short.toInt()
            val privateKey = ByteArray(privateKeyLen)
            buffer.get(privateKey)

            val clientIdLen = buffer.short.toInt()
            val clientId = ByteArray(clientIdLen)
            buffer.get(clientId)

            return V2(version, type, privateKey, clientId)
        }
    }
}

class Cdm(
    val deviceType: DeviceTypes,
    val clientId: ClientIdentification,
    val rsaKey: RSAPrivateKey,
) {
    fun getLicenseChallenge(
        pssh: ProtectionSystemHeaderBox,
        licenseType: LicenseType = LicenseType.STREAMING,
    ): String {
        val requestId = if (deviceType == DeviceTypes.ANDROID) {
            val randomBytes = ByteArray(4)
            SecureRandom().nextBytes(randomBytes)

            val sessionNumber = 1
            val counterBytes = ByteArray(8)
            for (i in counterBytes.indices) {
                counterBytes[i] = ((sessionNumber shr (i * 8)) and 0xFF).toByte()
            }

            val requestId = randomBytes + ByteArray(4) + counterBytes
            requestId.toHexString().uppercase().toByteArray()
        } else {
            generateRandomBytes(16)
        }

        val licenceRequest = LicenseRequest(
            clientId = clientId,
            encryptedClientId = null,
            contentId = LicenseRequest.ContentIdentification(
                widevinePsshData = LicenseRequest.ContentIdentification.WidevinePsshData(
                    psshData = listOf(pssh.initData),
                    licenseType = licenseType,
                    requestId = requestId,
                ),
            ),
            type = LicenseRequest.RequestType.NEW,
            requestTime = System.currentTimeMillis() / 1000L,
            protocolVersion = ProtocolVersion.VERSION_2_1,
            keyControlNonce = Random.nextUInt(1u, UInt.MAX_VALUE),
        ).let { ProtoBuf.encodeToByteArray(it) }

        return SignedMessage(
            type = SignedMessage.MessageType.LICENSE_REQUEST,
            msg = licenceRequest,
            signature = sign(licenceRequest, rsaKey),
        ).let { ProtoBuf.encodeToByteArray(it).toBase64() }
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        this.copyInto(result, 0, 0, this.size)
        other.copyInto(result, this.size, 0, other.size)
        return result
    }

    companion object {
        fun fromData(wvdData: String): Cdm {
            val parsed = V2.parseStream(wvdData.decodeBase64())

            val clientData = ProtoBuf.decodeFromByteArray<ClientIdentification>(parsed.clientId)

            return Cdm(
                deviceType = parsed.type,
                clientId = clientData,
                rsaKey = getKey(parsed.privateKey),
            )
        }
    }
}
