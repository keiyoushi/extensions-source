package eu.kanade.tachiyomi.extension.zh.baozimhorg

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.goda.GoDa
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GoDaManhua : GoDa("GoDa漫画", "", "zh"), ConfigurableSource {

    override val id get() = 774030471139699415

    override val baseUrl: String

    init {
        val mirrors = MIRRORS
        if (System.getenv("CI") == "true") {
            baseUrl = mirrors.joinToString("#, ") { "https://$it" }
        } else {
            val mirrorIndex = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
                .getString(MIRROR_PREF, "0")!!.toInt().coerceAtMost(mirrors.size - 1)
            baseUrl = "https://" + mirrors[mirrorIndex]
        }
    }

    override val client = super.client.newBuilder().addInterceptor(NotFoundInterceptor()).build()

    private val json: Json = Injekt.get()

    override fun fetchChapterList(mangaId: String): List<SChapter> {
        val response = client.newCall(GET("https://api-get-v2.mgsearcher.com/api/manga/get?mid=$mangaId&mode=all", headers)).execute()
        return json.decodeFromString<ResponseDto<ChapterListDto>>(response.body.string()).data.toChapterList()
    }

    override fun pageListRequest(mangaId: String, chapterId: String): Request {
        if (mangaId.isEmpty() || chapterId.isEmpty()) throw Exception("请刷新漫画")
        return GET("https://api-get-v2.mgsearcher.com/api/chapter/getinfo?m=$mangaId&c=$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return json.decodeFromString<ResponseDto<PageListDto>>(response.body.string()).data.info.images.images.map { it.toPage() }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            val mirrors = MIRRORS
            key = MIRROR_PREF
            title = "镜像网址"
            summary = "%s\n重启生效"
            entries = mirrors
            entryValues = Array(mirrors.size, Int::toString)
            setDefaultValue("0")
        }.let(screen::addPreference)
    }
}

private const val MIRROR_PREF = "MIRROR"

// https://nav.telltome.net/
private val MIRRORS get() = arrayOf("baozimh.org", "godamh.com", "m.baozimh.one")

private class NotFoundInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 404) return response
        response.close()
        throw IOException("请将此漫画重新迁移到本图源")
    }
}
