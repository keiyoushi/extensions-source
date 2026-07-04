package keiyoushi.gradle.extensions

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.Serializable

sealed interface BaseUrlSpec : Serializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(
        val mirrors: List<String>,
        val withCustom: Boolean = false,
        val entries: List<String>? = null,
        val values: List<String>? = null,
    ) : BaseUrlSpec
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
    abstract val entries: ListProperty<String>
    abstract val values: ListProperty<String>

    val mirror = MirrorAdder()
    val mirrorSpecial = SpecialMirrorAdder()

    inner class MirrorAdder {
        fun add(url: String) {
            this@BaseUrlDsl.mirrors.add(url)
        }
        fun add(url: String, entry: String) {
            this@BaseUrlDsl.mirrors.add(url)
            this@BaseUrlDsl.entries.add(entry)
        }
        fun add(url: String, entry: String, value: String) {
            this@BaseUrlDsl.mirrors.add(url)
            this@BaseUrlDsl.entries.add(entry)
            this@BaseUrlDsl.values.add(value)
        }
    }

    inner class SpecialMirrorAdder {
        fun add(url: String) {
            mirror.add(url)
            this@BaseUrlDsl.withCustom.set(true)
        }
        fun add(url: String, entry: String) {
            mirror.add(url, entry)
            this@BaseUrlDsl.withCustom.set(true)
        }
        fun add(url: String, entry: String, value: String) {
            mirror.add(url, entry, value)
            this@BaseUrlDsl.withCustom.set(true)
        }
    }

    internal fun build(baseUrl: String): BaseUrlSpec {
        val custom = withCustom.getOrElse(false)
        val mirrorList = mirrors.getOrElse(emptyList())
        val entryList = entries.getOrElse(emptyList())
        val valueList = values.getOrElse(emptyList())

        return when {
            mirrorList.isNotEmpty() -> {
                BaseUrlSpec.Mirrors(
                    mirrors = listOf(baseUrl) + mirrorList,
                    withCustom = custom,
                    entries = entryList.takeIf { it.isNotEmpty() },
                    values = valueList.takeIf { it.isNotEmpty() },
                )
            }
            custom -> BaseUrlSpec.Custom(baseUrl)
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }
}
