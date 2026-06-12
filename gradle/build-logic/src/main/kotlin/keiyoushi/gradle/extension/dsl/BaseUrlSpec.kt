package keiyoushi.gradle.extension.dsl

import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.provider.Property
import java.io.Serializable as JvmSerializable
import java.net.URI

sealed interface BaseUrlSpec : JvmSerializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(val mirrors: List<Mirror>) : BaseUrlSpec
    data class Custom(override val defaultUrl: String) : BaseUrlSpec

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

        return when {
            custom -> BaseUrlSpec.Custom(baseUrl)
            mirrorsInternal.isNotEmpty() -> {
                val default = Mirror(labelOf(baseUrl), baseUrl)
                BaseUrlSpec.Mirrors(listOf(default) + mirrorsInternal)
            }
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }

    private fun labelOf(url: String): String =
        runCatching { URI(url).host }.getOrDefault(url)
}
