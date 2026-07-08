package keiyoushi.gradle.extensions

import java.io.Serializable

data class Mirror(
    val url: String,
    val label: String? = null,
    val value: String? = null,
) : Serializable

sealed interface BaseUrlSpec : Serializable {
    data class Static(val url: String) : BaseUrlSpec
    data class Mirrors(val mirrors: List<Mirror>) : BaseUrlSpec
    data class Custom(override val defaultUrl: String, val mirrors: List<Mirror> = emptyList()) : BaseUrlSpec

    val defaultUrl: String
        get() = when (this) {
            is Static -> url
            is Mirrors -> mirrors.first().url
            is Custom -> defaultUrl
        }

    fun allUrls(): List<String> = when (this) {
        is Static -> listOf(url)
        is Mirrors -> mirrors.map { it.url }
        is Custom -> listOf(defaultUrl) + mirrors.map { it.url }
    }
}

class BaseUrlDsl {
    private val mirrors = mutableListOf<Mirror>()
    private var customUrl: String? = null

    /** Declares selectable mirror urls; the first is the default and each mirror's host is shown in the picker. */
    fun mirrors(vararg urls: String) {
        urls.mapTo(mirrors) { Mirror(it) }
    }

    /** Declares selectable mirrors as `"label" to "url"` pairs; the first is the default and its label is shown in the picker. */
    fun mirrors(vararg labeled: Pair<String, String>) {
        labeled.mapTo(mirrors) { (label, url) -> Mirror(url, label) }
    }

    /** Declares a user-editable base url; [defaultUrl] is the placeholder shown until the user overrides it. */
    fun custom(defaultUrl: String) {
        customUrl = defaultUrl
    }

    val mirrorSpecial = MirrorSpecialSpec()

    inner class MirrorSpecialSpec {
        fun add(url: String, label: String, value: String) {
            mirrors.add(Mirror(url, label, value))
        }
    }

    internal fun build(): BaseUrlSpec {
        val custom = customUrl
        if (custom != null) {
            return BaseUrlSpec.Custom(custom, mirrors.toList())
        }
        check(mirrors.isNotEmpty()) { "source { baseUrl { } } is empty — call mirrors(...), custom(...) or mirrorSpecial" }
        return BaseUrlSpec.Mirrors(mirrors.toList())
    }
}

