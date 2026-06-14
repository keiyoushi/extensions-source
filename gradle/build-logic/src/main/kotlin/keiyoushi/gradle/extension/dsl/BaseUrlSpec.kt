package keiyoushi.gradle.extension.dsl

import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.provider.Property
import java.io.Serializable

sealed interface BaseUrlSpec : Serializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(val mirrors: List<String>, val prefKey: String) : BaseUrlSpec
    data class Custom(override val defaultUrl: String, val prefKey: String) : BaseUrlSpec

    val defaultUrl: String
        get() = when (this) {
            is Static -> url
            is Mirrors -> mirrors.first()
            is Custom -> defaultUrl
        }
}

abstract class BaseUrlDsl {
    abstract val value: Property<String>
    abstract val withCustomUrl: Property<Boolean>
    abstract val prefKey: Property<String>

    private val mirrorsInternal = mutableListOf<String>()

    fun mirror(url: String) {
        mirrorsInternal.add(url)
    }

    internal fun build(): BaseUrlSpec {
        val baseUrl = value.get()
        val custom = withCustomUrl.getOrElse(false)

        assertWithoutFlag(!(custom && mirrorsInternal.isNotEmpty())) {
            "Cannot use both mirror() and withCustomUrl = true"
        }

        val key = prefKey.getOrElse("")

        return when {
            custom -> BaseUrlSpec.Custom(baseUrl, key)
            mirrorsInternal.isNotEmpty() -> {
                BaseUrlSpec.Mirrors(listOf(baseUrl) + mirrorsInternal, key)
            }
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }
}
