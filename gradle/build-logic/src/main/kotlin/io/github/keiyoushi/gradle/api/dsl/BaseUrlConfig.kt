package io.github.keiyoushi.gradle.api.dsl

import io.github.keiyoushi.gradle.internal.BaseUrl
import io.github.keiyoushi.gradle.internal.BaseUrl.Mirror

class BaseUrlConfig {
    private val mirrors = mutableListOf<Mirror>()
    private var custom: BaseUrl.Custom? = null

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
        custom = BaseUrl.Custom(defaultUrl)
    }

    internal fun build(): BaseUrl {
        check(!(mirrors.isNotEmpty() && custom != null)) {
            "source { baseUrl { } }: use mirrors(...) or custom(...), not both"
        }
        return custom
            ?: mirrors.takeIf { it.isNotEmpty() }?.let { BaseUrl.Mirrors(it.toList()) }
            ?: error("source { baseUrl { } } is empty — call mirrors(...) or custom(...)")
    }
}
