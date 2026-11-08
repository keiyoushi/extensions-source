package eu.kanade.tachiyomi.extension.zh.acgn

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class CategoryDto(val name: String, val path: String)

class CseConfig(val token: String, val libVersion: String, val exp: String)

@Serializable
class CseResultDto(
    val cursor: CseCursorDto? = null,
    val results: List<CseItemDto> = emptyList(),
    val error: JsonObject? = null,
)

@Serializable
class CseCursorDto(val pages: List<CsePageDto> = emptyList())

@Serializable
class CsePageDto(val start: String)

@Serializable
class CseItemDto(
    private val unescapedUrl: String,
    private val richSnippet: CseRichSnippetDto? = null,
) {
    fun toSManga(baseHost: String): SManga? {
        val url = unescapedUrl.toHttpUrl()
        if (url.host != baseHost) return null
        val slug = url.pageId("manhua-") ?: return null
        val metatags = richSnippet?.metatags ?: return null
        val title = metatags.ogTitle?.substringAfter("《")?.substringBefore("》") ?: return null
        return SManga.create().apply {
            this.url = slug
            this.title = title
            thumbnail_url = metatags.ogImage
        }
    }
}

@Serializable
class CseRichSnippetDto(val metatags: CseMetatagsDto? = null)

@Serializable
class CseMetatagsDto(val ogTitle: String? = null, val ogImage: String? = null)
