package keiyoushi.gradle.extension.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.Serializable as JvmSerializable

@Serializable
sealed interface OverrideValue : JvmSerializable {
    @Serializable
    @SerialName("str")
    data class Str(val v: String) : OverrideValue

    @Serializable
    @SerialName("int")
    data class IntV(val v: Int) : OverrideValue

    @Serializable
    @SerialName("long")
    data class LongV(val v: Long) : OverrideValue

    @Serializable
    @SerialName("bool")
    data class BoolV(val v: Boolean) : OverrideValue
}
