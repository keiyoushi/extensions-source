package keiyoushi.gradle.extension.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject
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
    data class Mirrors(val mirrors: List<Mirror>) : BaseUrlSpec

    @Serializable
    @SerialName("custom")
    data class Custom(val defaultUrl: String) : BaseUrlSpec
}

@Serializable
data class Mirror(val label: String, val url: String) : JvmSerializable

val BaseUrlSpec.defaultUrl: String
    get() = when (this) {
        is BaseUrlSpec.Static -> url
        is BaseUrlSpec.Mirrors -> mirrors.first().url
        is BaseUrlSpec.Custom -> defaultUrl
    }

abstract class BaseUrlDsl @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val value: Property<String>
    abstract val mirrors: ListProperty<Any> // Support both String and Pair
    abstract val withCustomUrl: Property<Boolean>

    internal fun build(): BaseUrlSpec {
        val baseUrl = value.get()
        val custom = withCustomUrl.getOrElse(false)
        val mirrorList = mirrors.orNull.orEmpty()

        return when {
            custom -> BaseUrlSpec.Custom(baseUrl)
            mirrorList.isNotEmpty() -> {
                val resolved = mirrorList.map { m ->
                    when (m) {
                        is String -> Mirror(m, m)
                        is Pair<*, *> -> Mirror(m.first.toString(), m.second.toString())
                        else -> throw IllegalArgumentException("Unsupported mirror type: ${m.javaClass}")
                    }
                }
                BaseUrlSpec.Mirrors(resolved)
            }
            else -> BaseUrlSpec.Static(baseUrl)
        }
    }
}
