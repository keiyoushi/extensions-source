package eu.kanade.tachiyomi.extension.id.astralscans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.textOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody

@Source
abstract class AstralScans : MangaThemesia() {

    override val hasProjectPage = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val body = FormBody.Builder()
            .add("asx_req", "chapters")
            .build()

        val headers = headersBuilder()
            .add("X-Alt-Auth", "1")
            .build()

        val response = client.post(
            getMangaUrl(manga),
            headers = headers,
            body = body,
        )

        val responseText = response.body.string()
        return if (responseText.startsWith("ASX_")) {
            parseChapters(responseText)
        } else {
            super.chapterListParse(responseText.asJsoup(baseUrl))
        }
    }

    private fun parseChapters(responseText: String): List<SChapter> {
        val payload = rot13(responseText.removePrefix("ASX_"))
        val decoded = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8).reversed()
        val parts = decoded.split("|||")
        if (parts.size < 2) return emptyList()

        val dynamicDataAttr = parts[1]
        return parts[0].asJsoup(baseUrl).select("[$dynamicDataAttr]").mapNotNull { element ->
            val chapterUrl = String(Base64.decode(element.attr(dynamicDataAttr), Base64.DEFAULT), Charsets.UTF_8)

            // Decoy entries point at a protect page and are hidden by the site's own CSS
            if (chapterUrl.contains("/?protect=")) {
                return@mapNotNull null
            }

            // The first span is a hidden "Read"/"Info" label for the site's own chapter search
            val spans = element.select("span")
            SChapter.create().apply {
                setUrlWithoutDomain(chapterUrl)
                name = spans.getOrNull(1)?.textOrNull() ?: "Chapter"
                date_upload = spans.getOrNull(2)?.textOrNull()?.parseChapterDate() ?: 0L
            }
        }
    }

    private fun rot13(text: String) = text.map {
        when (it) {
            in 'a'..'m', in 'A'..'M' -> it + 13
            in 'n'..'z', in 'N'..'Z' -> it - 13
            else -> it
        }
    }.joinToString("")
}
