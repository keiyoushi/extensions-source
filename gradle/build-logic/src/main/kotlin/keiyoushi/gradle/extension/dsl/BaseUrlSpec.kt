package keiyoushi.gradle.extension.dsl

import java.io.Serializable

sealed interface BaseUrlSpec : Serializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(val urls: List<String>) : BaseUrlSpec
    data class Custom(val defaultUrl: String) : BaseUrlSpec
}

val BaseUrlSpec.defaultUrl: String
    get() = when (this) {
        is BaseUrlSpec.Static -> url
        is BaseUrlSpec.Mirrors -> urls.first()
        is BaseUrlSpec.Custom -> defaultUrl
    }

class MirrorBuilder {
    private val urls = mutableListOf<String>()
    fun mirror(url: String) { urls.add(url) }
    internal fun build(): BaseUrlSpec.Mirrors {
        require(urls.isNotEmpty()) { "mirrorUrls must have at least one entry" }
        return BaseUrlSpec.Mirrors(urls.toList())
    }
}

fun mirrorUrls(vararg urls: String): BaseUrlSpec.Mirrors {
    require(urls.isNotEmpty()) { "mirrorUrls must have at least one entry" }
    return BaseUrlSpec.Mirrors(urls.toList())
}

fun mirrorUrls(block: MirrorBuilder.() -> Unit): BaseUrlSpec.Mirrors =
    MirrorBuilder().apply(block).build()

fun customBaseUrl(default: String): BaseUrlSpec.Custom = BaseUrlSpec.Custom(default)
