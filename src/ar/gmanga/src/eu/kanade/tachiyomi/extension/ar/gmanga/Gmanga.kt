package eu.kanade.tachiyomi.extension.ar.gmanga

import android.app.Application
import eu.kanade.tachiyomi.multisrc.gmanga.BrowseManga
import eu.kanade.tachiyomi.multisrc.gmanga.Gmanga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Gmanga : Gmanga(
    "GMANGA",
    "https://gmanga.org",
    "ar",
    "https://media.gmanga.me",
) {
    override val client = super.client.newBuilder()
        .rateLimit(4)
        .build()

    init {
        // remove obsolete preferences
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000).run {
            if (contains("gmanga_chapter_listing")) {
                edit().remove("gmanga_chapter_listing").apply()
            }
            if (contains("gmanga_last_listing")) {
                edit().remove("gmanga_last_listing").apply()
            }
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val decMga = response.decryptAs<JsonObject>()
        val selectedManga = decMga["rows"]!!.jsonArray[0].jsonObject["rows"]!!.jsonArray
        val manags = selectedManga.map {
            json.decodeFromJsonElement<BrowseManga>(it.jsonArray[17])
        }

        val entries = manags.map { it.toSManga(::createThumbnail) }
            .distinctBy { it.url }

        return MangasPage(
            entries,
            hasNextPage = (manags.size >= 30),
        )
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservable() // sites returns false 302 code
            .map(::chapterListParse)
    }

    override fun chaptersRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("https://api2.gmanga.me/api/mangas/$mangaId/releases", headers)
    }

    override fun chaptersParse(response: Response): List<SChapter> {
        val chapterList = response.parseAs<ChapterListResponse>()

        return chapterList.releases.map {
            SChapter.create().apply {
                val chapter = chapterList.chapterizations.first { chap -> chap.id == it.chapId }
                val team = chapterList.teams.firstOrNull { team -> team.id == it.teamId }

                url = "/r/${it.id}"
                chapter_number = it.chapter.float
                date_upload = it.timestamp * 1000
                scanlator = team?.name

                val chapterName = chapter.title.let { if (it.trim() != "") " - $it" else "" }
                name = "${chapter_number.let { if (it % 1 > 0) it else it.toInt() }}$chapterName"
            }
        }
    }
}
