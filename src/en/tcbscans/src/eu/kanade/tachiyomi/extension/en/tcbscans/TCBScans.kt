package eu.kanade.tachiyomi.extension.en.tcbscans

import android.app.Application
import android.util.Log
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.OkHttpClient
import okio.IOException
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Source
abstract class TCBScans : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addNetworkInterceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (
            request.url.toString().startsWith(baseUrl) &&
            request.url.pathSegments.firstOrNull() in listOf("mangas", "chapters") &&
            response.code == 404
        ) {
            throw IOException("Migrate from TCB Scans to TCB Scans")
        }

        return@addNetworkInterceptor response
    }

    // popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/projects").asJsoup()
        val mangas = document.select("div.bg-card").map { element ->
            SManga.create().apply {
                with(element.selectFirst("a[href].text-white")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    title = text()
                }
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // search
    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val mangas = getPopularManga(page).mangas.filter {
            it.title.contains(query, true)
        }
        return MangasPage(mangas, false)
    }

    // manga details & chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            with(document.selectFirst("div.order-1")!!) {
                thumbnail_url = selectFirst("img")?.absUrl("src")
                title = selectFirst("h1")!!.text()
                description = selectFirst("p")?.text()
            }
        }

        val chapterList = document.select("div.grid a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))

                val title = element.select("div.font-bold:not(.flex)").text()
                val description = element.selectFirst(".text-gray-500")
                    ?.text()?.takeIf { it.isNotEmpty() }
                val chapNumber = TITLE_REGEX.find(title)?.value

                name = buildString {
                    if (chapNumber != null) {
                        append("Chapter ")
                        append(chapNumber)
                    } else {
                        append(title)
                    }
                    if (description != null) {
                        append(": ")
                        append(description)
                    }
                }
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    // pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("picture img, .image-container img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    init {
        val context = Injekt.get<Application>()
        val prefs = getPreferences()

        if (!prefs.getBoolean("legacy_updateTime_removed", false)) {
            try {
                val sharedPrefDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (sharedPrefDir.exists() && sharedPrefDir.isDirectory()) {
                    val files = sharedPrefDir.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (
                                file.isFile &&
                                file.name.startsWith("source_${id}_updateTime") &&
                                file.name.endsWith(".xml")
                            ) {
                                Log.d(name, "Deleting ${file.name}")
                                file.delete()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                Log.e(name, "Failed to delete old preference files")
            }
            prefs.edit()
                .putBoolean("legacy_updateTime_removed", true)
                .apply()
        }
    }
}

private val TITLE_REGEX = Regex("""\d+.?\d+$""")
