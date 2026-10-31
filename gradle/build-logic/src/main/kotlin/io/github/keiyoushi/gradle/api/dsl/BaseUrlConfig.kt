package io.github.keiyoushi.gradle.api.dsl

import io.github.keiyoushi.gradle.internal.BaseUrl
import io.github.keiyoushi.gradle.internal.BaseUrl.Mirror

class BaseUrlConfig {
    private val mirrors = mutableListOf<Mirror>()
    private var custom: BaseUrl.Custom? = null
    private var fetched: BaseUrl.Fetched? = null

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

    /**
     * Declares base urls the source looks up at runtime with `fetchBaseUrls`; [fallbackUrls] are used until the first
     * lookup succeeds.
     */
    fun fetched(vararg fallbackUrls: String) {
        require(fallbackUrls.isNotEmpty()) { "baseUrl { fetched(...) } needs at least one url" }
        fetched = BaseUrl.Fetched(fallbackUrls.toList())
    }

    internal fun build(): BaseUrl {
        check(listOf(mirrors.isNotEmpty(), custom != null, fetched != null).count { it } <= 1) {
            "source { baseUrl { } }: use only one of mirrors(...), custom(...) or fetched(...)"
        }
        return custom
            ?: fetched
            ?: mirrors.takeIf { it.isNotEmpty() }?.let { BaseUrl.Mirrors(it.toList()) }
            ?: error("source { baseUrl { } } is empty — call mirrors(...), custom(...) or fetched(...)")
    }
}
