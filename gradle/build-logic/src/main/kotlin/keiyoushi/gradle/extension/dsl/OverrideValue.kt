package keiyoushi.gradle.extension.dsl

import java.io.Serializable as JvmSerializable

sealed interface OverrideValue : JvmSerializable {
    data class Str(val v: String) : OverrideValue
    data class IntV(val v: Int) : OverrideValue
    data class LongV(val v: Long) : OverrideValue
    data class BoolV(val v: Boolean) : OverrideValue
}
