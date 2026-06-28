package keiyoushi.gradle.extensions

import org.gradle.api.provider.ListProperty
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
    abstract val withCustom: Property<Boolean>
    abstract val mirrors: ListProperty<String>

    internal fun build(baseUrl: String): BaseUrlSpec {
        val custom = withCustom.getOrElse(false)
        val mirrorList = mirrors.getOrElse(emptyList())
        check(!(custom && mirrorList.isNotEmpty())) {
            "Cannot use both mirrors and withCustom = true in source { baseUrl(...) { ... } }"
        }
        return when {
            custom -> BaseUrlSpec.Custom(baseUrl)
            mirrorList.isNotEmpty() -> BaseUrlSpec.Mirrors(listOf(baseUrl) + mirrorList)
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }
}
