package keiyoushi.gradle.extension.dsl

import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.provider.Property
import java.io.Serializable as JvmSerializable
import java.net.URI

sealed interface BaseUrlSpec : JvmSerializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(val mirrors: List<Mirror>, val prefKey: String) : BaseUrlSpec
    data class Custom(override val defaultUrl: String, val prefKey: String) : BaseUrlSpec

    val defaultUrl: String
        get() = when (this) {
            is Static -> url
            is Mirrors -> mirrors.first().url
            is Custom -> defaultUrl
        }
}

data class Mirror(val label: String, val url: String) : JvmSerializable

abstract class BaseUrlDsl {
    abstract val value: Property<String>
    abstract val withCustomUrl: Property<Boolean>
    abstract val prefKey: Property<String>

    private val mirrorsInternal = mutableListOf<Mirror>()

    fun mirror(url: String) {
        mirrorsInternal.add(Mirror(labelOf(url), url))
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
                val default = Mirror(labelOf(baseUrl), baseUrl)
                BaseUrlSpec.Mirrors(listOf(default) + mirrorsInternal, key)
            }
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }

    private fun labelOf(url: String): String =
        runCatching { URI(url).host }.getOrDefault(url)
}
