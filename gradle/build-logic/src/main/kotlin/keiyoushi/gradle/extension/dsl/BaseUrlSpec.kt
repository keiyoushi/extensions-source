package keiyoushi.gradle.extension.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.Serializable as JvmSerializable

@Serializable
sealed interface BaseUrlSpec : JvmSerializable {
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

val BaseUrlSpec.defaultUrl: String
    get() = when (this) {
        is BaseUrlSpec.Static -> url
        is BaseUrlSpec.Mirrors -> urls.first()
        is BaseUrlSpec.Custom -> defaultUrl
    }

fun mirrorUrls(vararg urls: String): BaseUrlSpec.Mirrors {
    require(urls.isNotEmpty()) { "mirrorUrls must have at least one entry" }
    return BaseUrlSpec.Mirrors(urls.toList())
}

fun customBaseUrl(default: String): BaseUrlSpec.Custom = BaseUrlSpec.Custom(default)
