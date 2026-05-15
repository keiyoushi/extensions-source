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

@Serializable(with = ReactFlightBigIntSerializer::class)
class ReactFlightBigInt : BigInteger {
    constructor(value: BigInteger) : super(value.toString())

    override fun toByte(): Byte = toInt().toByte()
    override fun toShort(): Short = toInt().toShort()
}

object ReactFlightBigIntSerializer : KSerializer<ReactFlightBigInt> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReactFlightBigInt", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ReactFlightBigInt): Unit = throw SerializationException("Stub !")

    override fun deserialize(decoder: Decoder): ReactFlightBigInt {
        val raw = decoder.decodeString()
        return ReactFlightBigInt(
            runCatching { BigInteger(raw) }.getOrNull()
                ?: throw IllegalArgumentException("Failed to parse BigInt: $raw"),
        )
    }
}
