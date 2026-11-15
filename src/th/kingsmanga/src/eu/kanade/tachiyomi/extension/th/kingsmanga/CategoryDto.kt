package eu.kanade.tachiyomi.extension.th.kingsmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Serializable
class CategoryDto(
    private val name: String,
    private val link: String,
) {
    fun toSManga() = SManga.create().apply {
        url = link.toHttpUrl().encodedPath
        title = Parser.unescapeEntities(name, false)
    }
}
