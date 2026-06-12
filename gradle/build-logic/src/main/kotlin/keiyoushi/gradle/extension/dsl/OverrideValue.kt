package keiyoushi.gradle.extension.dsl

import java.io.Serializable

sealed interface OverrideValue : Serializable {
    data class Str(val v: String) : OverrideValue
    data class IntV(val v: Int) : OverrideValue
    data class LongV(val v: Long) : OverrideValue
    data class BoolV(val v: Boolean) : OverrideValue
}
