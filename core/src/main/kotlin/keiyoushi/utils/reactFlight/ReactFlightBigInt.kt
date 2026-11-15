package keiyoushi.utils.reactFlight

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigInteger

/** A [BigInteger] whose React Flight `$n<digits>` string is parsed by [ReactFlightBigIntSerializer]. */
typealias ReactFlightBigInt =
    @Serializable(with = ReactFlightBigIntSerializer::class)
    BigInteger

object ReactFlightBigIntSerializer : KSerializer<BigInteger> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReactFlightBigInt", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigInteger): Unit = throw SerializationException("Stub !")

    override fun deserialize(decoder: Decoder): BigInteger {
        val raw = decoder.decodeString()
        return runCatching { BigInteger(raw) }.getOrNull()
            ?: throw IllegalArgumentException("Failed to parse BigInt: $raw")
    }
}
