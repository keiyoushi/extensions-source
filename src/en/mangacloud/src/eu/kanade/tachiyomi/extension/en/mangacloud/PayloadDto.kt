package eu.kanade.tachiyomi.extension.en.mangacloud

import kotlinx.serialization.Serializable

@Serializable
class PagePayload(
    private val page: Int,
)

@Serializable
class SearchPayload(
    private val title: String? = null,
    private val type: String? = null,
    private val sort: String? = null,
    private val status: String? = null,
    private val includes: List<String> = emptyList(),
    private val excludes: List<String> = emptyList(),
    private val page: Int,
)
