package keiyoushi.gradle.extensions

import org.gradle.api.provider.Property
import java.io.Serializable

sealed interface BaseUrlSpec : Serializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(val mirrors: List<String>) : BaseUrlSpec
    data class Custom(override val defaultUrl: String) : BaseUrlSpec

    val defaultUrl: String
        get() = when (this) {
            is Static -> url
            is Mirrors -> mirrors.first()
            is Custom -> defaultUrl
        }

    fun allUrls(): List<String> = when (this) {
        is Static -> listOf(url)
        is Mirrors -> mirrors
        is Custom -> listOf(defaultUrl)
    }
}

abstract class BaseUrlDsl {
    abstract val withCustomUrl: Property<Boolean>

    private val mirrorsInternal = mutableListOf<String>()

    fun mirror(url: String) {
        mirrorsInternal.add(url)
    }

    internal fun build(baseUrl: String): BaseUrlSpec {
        val custom = withCustomUrl.getOrElse(false)
        check(!(custom && mirrorsInternal.isNotEmpty())) {
            "Cannot use both mirror() and withCustomUrl = true in source { baseUrl(...) { ... } }"
        }
        return when {
            custom -> BaseUrlSpec.Custom(baseUrl)
            mirrorsInternal.isNotEmpty() -> BaseUrlSpec.Mirrors(listOf(baseUrl) + mirrorsInternal)
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }
}
