package keiyoushi.gradle.extension.dsl

import org.gradle.api.provider.Property
import java.io.Serializable as JvmSerializable

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
        mirrorsInternal.add(Mirror(url, url))
    }

    fun mirror(label: String, url: String) {
        mirrorsInternal.add(Mirror(label, url))
    }

    internal fun build(): BaseUrlSpec {
        val baseUrl = value.get()
        val custom = withCustomUrl.getOrElse(false)

        return when {
            custom -> BaseUrlSpec.Custom(baseUrl)
            mirrorsInternal.isNotEmpty() -> BaseUrlSpec.Mirrors(mirrorsInternal.toList())
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }
}
