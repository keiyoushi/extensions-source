package eu.kanade.tachiyomi.extension.zh.yiqiqimanhua

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class PostLink(val link: String)

@Serializable
class Post(
    @SerialName("title") private val postTitle: Rendered,
    @SerialName("date_gmt") private val dateGmt: String,
    private val categories: List<Int>,
    private val content: Rendered,
) {
    fun toSManga(manga: SManga, baseUrl: String) = manga.apply {
        title = Parser.unescapeEntities(postTitle.rendered, false)
        genre = categories.mapNotNull { id -> CATEGORIES.find { it.id == id }?.name }.joinToString().ifEmpty { null }
        thumbnail_url = imageUrls(baseUrl).firstOrNull()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter(id: String) = SChapter.create().apply {
        url = id
        name = "Ch. 1"
        date_upload = DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(dateGmt, ZoneOffset.UTC)
    }

    fun imageUrls(baseUrl: String) = Jsoup.parseBodyFragment(content.rendered, baseUrl).select("img[src]")
        .map { it.absUrl("src").toSecureUrl() }
}

@Serializable
class Rendered(val rendered: String)

// Images are linked over http, which browsers load over https
fun String.toSecureUrl() = toHttpUrl().newBuilder().scheme("https").build().toString()
