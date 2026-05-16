package keiyoushi.compiler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Spec wire format: produced by build-logic at Gradle config time, encoded as base64 JSON,
// passed as the "keiyoushi.spec" KSP option, decoded here. The @SerialName tags are the
// contract between the two sides.

@Serializable
data class ExtensionSpec(
    val name: String,
    val pkg: String,
    val sources: List<SourceSpec>,
)

@Serializable
data class SourceSpec(
    val name: String,
    val lang: String,
    val versionId: Int,
    val id: Long,
    val baseUrl: BaseUrlSpec,
    val overrides: Map<String, OverrideValue>,
)

@Serializable
sealed interface BaseUrlSpec {
    @Serializable
    @SerialName("static")
    data class Static(val url: String) : BaseUrlSpec

    @Serializable
    @SerialName("mirrors")
    data class Mirrors(val urls: List<String>) : BaseUrlSpec

    @Serializable
    @SerialName("custom")
    data class Custom(val defaultUrl: String) : BaseUrlSpec
}

@Serializable
sealed interface OverrideValue {
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
