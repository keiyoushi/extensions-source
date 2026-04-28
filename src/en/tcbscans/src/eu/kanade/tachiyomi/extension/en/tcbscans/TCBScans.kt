package eu.kanade.tachiyomi.extension.en.tcbscans

import android.app.Application
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class TCBScans : HttpSource() {

    override val name = "TCB Scans"
    override val baseUrl = "https://tcbonepiecechapters.com"
    override val lang = "en"
    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder().addNetworkInterceptor { chain ->
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
    }.build()

    // popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/projects", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
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
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val response = client.newCall(popularMangaRequest(page)).execute()
        val mangas = popularMangaParse(response).mangas.filter {
            it.title.contains(query, true)
        }
        return Observable.just(MangasPage(mangas, false))
    }

    // manga details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            with(document.selectFirst("div.order-1")!!) {
                thumbnail_url = selectFirst("img")?.absUrl("src")
                title = selectFirst("h1")!!.text()
                description = selectFirst("p")?.text()
            }
        }
    }

    // chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.grid a").map { element ->
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
    }

    // pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("picture img, .image-container img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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
