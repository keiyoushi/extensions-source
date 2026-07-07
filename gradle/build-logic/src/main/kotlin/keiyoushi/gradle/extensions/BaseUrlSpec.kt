package keiyoushi.gradle.extensions

import java.io.Serializable

data class Mirror(val url: String, val label: String? = null) : Serializable

sealed interface BaseUrlSpec : Serializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(val mirrors: List<Mirror>) : BaseUrlSpec
    data class Custom(override val defaultUrl: String) : BaseUrlSpec

    val defaultUrl: String
        get() = when (this) {
            is Static -> url
            is Mirrors -> mirrors.first().url
            is Custom -> defaultUrl
        }

    fun allUrls(): List<String> = when (this) {
        is Static -> listOf(url)
        is Mirrors -> mirrors.map { it.url }
        is Custom -> listOf(defaultUrl)
    }
}

class BaseUrlDsl {
    private val mirrors = mutableListOf<Mirror>()
    private var custom: BaseUrlSpec.Custom? = null

    /** Declares selectable mirror urls; the first is the default and each mirror's host is shown in the picker. */
    fun mirrors(vararg urls: String) {
        require(urls.isNotEmpty()) { "baseUrl { mirrors(...) } needs at least one url" }
        urls.mapTo(mirrors) { Mirror(it) }
    }

    /** Declares selectable mirrors as `"label" to "url"` pairs; the first is the default and its label is shown in the picker. */
    fun mirrors(vararg labeled: Pair<String, String>) {
        require(labeled.isNotEmpty()) { "baseUrl { mirrors(...) } needs at least one url" }
        labeled.mapTo(mirrors) { (label, url) -> Mirror(url, label) }
    }

    /** Declares a user-editable base url; [defaultUrl] is the placeholder shown until the user overrides it. */
    fun custom(defaultUrl: String) {
        custom = BaseUrlSpec.Custom(defaultUrl)
    }

    internal fun build(): BaseUrlSpec {
        check(!(mirrors.isNotEmpty() && custom != null)) {
            "source { baseUrl { } }: use mirrors(...) or custom(...), not both"
        }
        return custom
            ?: mirrors.takeIf { it.isNotEmpty() }?.let { BaseUrlSpec.Mirrors(it.toList()) }
            ?: error("source { baseUrl { } } is empty — call mirrors(...) or custom(...)")
    }
}
