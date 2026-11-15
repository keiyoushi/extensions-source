package io.github.keiyoushi.gradle.internal

import java.io.Serializable

internal sealed interface BaseUrl : Serializable {
    data class Static(val url: String) : BaseUrl
    data class Mirrors(val mirrors: List<Mirror>) : BaseUrl
    data class Custom(override val defaultUrl: String) : BaseUrl

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

    data class Mirror(val url: String, val label: String? = null) : Serializable
}
