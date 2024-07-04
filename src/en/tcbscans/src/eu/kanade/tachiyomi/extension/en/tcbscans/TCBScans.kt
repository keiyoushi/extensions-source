package eu.kanade.tachiyomi.extension.en.tcbscans

import android.app.Application
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class TCBScans : ParsedHttpSource() {

    override val name = "TCB Scans"
    override val baseUrl = "https://tcbscans.me"
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
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/projects", headers)
    }

    override fun popularMangaSelector() = "div.bg-card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("a[href].text-white")!!) {
            setUrlWithoutDomain(absUrl("href"))
            title = text()
        }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = null

    // latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/projects#$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment!!
        val mangas = popularMangaParse(response).mangas.filter {
            it.title.contains(query, true)
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = popularMangaSelector()

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        with(document.selectFirst("div.order-1")!!) {
            thumbnail_url = selectFirst("img")?.absUrl("src")
            title = selectFirst("h1")!!.text()
            description = selectFirst("p")?.text()
        }
    }

    // chapters
    override fun chapterListSelector() = "div.grid a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))

        val title = element.select("div.font-bold:not(.flex)").text()
        val description = element.selectFirst(".text-gray-500")
            ?.text()?.takeIf { it.isNotBlank() }
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

    override fun pageListParse(document: Document): List<Page> {
        return document.select("picture img, .image-container img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    init {
        val context = Injekt.get<Application>()
        val prefs = context.getSharedPreferences("source_$id", 0x0000)

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
