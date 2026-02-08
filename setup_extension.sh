#!/bin/bash

# Pre-flight Check: Ensure we are in the extensions-source root
if [ ! -f "settings.gradle.kts" ] && [ ! -f "settings.gradle" ]; then
    echo "Error: run this script from the extensions-source root folder"
    exit 1
fi

# SDK Check: Ensure local.properties exists
if [ ! -f "local.properties" ]; then
    echo -e "\033[0;31mWarning: local.properties not found. Android build will fail. Create it pointing to your SDK.\033[0m"
fi

# Define paths
PKG_ROOT="src/id/softkomik"
SRC_DIR="$PKG_ROOT/src/eu/kanade/tachiyomi/extension/id/softkomik"

# Create directories
mkdir -p "$SRC_DIR"

# Create build.gradle
cat <<'EOF' > "$PKG_ROOT/build.gradle"
ext {
    extName = 'Softkomik'
    extClass = '.Softkomik'
    extVersionCode = 1
    baseUrl = 'https://softkomik.com/'
}

apply from: "$rootDir/common.gradle"
EOF

# Create Softkomik.kt
cat <<'EOF' > "$SRC_DIR/Softkomik.kt"
package eu.kanade.tachiyomi.extension.id.softkomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Softkomik : ParsedHttpSource() {

    override val name = "Softkomik"
    override val baseUrl = "https://softkomik.com"
    override val lang = "id"
    override val supportsLatest = true

    private val json: Json by injectLazy()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/komik-terpopuler?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.grid > div"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst("h3")?.text() ?: "Unknown"
            thumbnail_url = element.selectFirst("img")?.attr("src")
            setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/komik-terbaru?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ============================== Details ===============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val nextData = getNextData(document)

        // Navigate JSON: props -> pageProps -> series/data
        val props = nextData["props"]?.jsonObject?.get("pageProps")?.jsonObject
        val series = props?.get("series")?.jsonObject 
            ?: props?.get("data")?.jsonObject
            ?: throw Exception("Could not find series data in JSON")

        return SManga.create().apply {
            title = series["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
            description = series["description"]?.jsonPrimitive?.contentOrNull
            genre = series["genres"]?.jsonArray?.joinToString { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" }
            thumbnail_url = series["cover"]?.jsonPrimitive?.contentOrNull
            author = series["author"]?.jsonPrimitive?.contentOrNull
            status = parseStatus(series["status"]?.jsonPrimitive?.contentOrNull)
        }
    }

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException("Not used")

    private fun parseStatus(status: String?): Int {
        return when (status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed", "tamat" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val nextData = getNextData(document)

        val props = nextData["props"]?.jsonObject?.get("pageProps")?.jsonObject
        // Chapters usually in 'chapters' array or 'series.chapters'
        val chaptersArray = props?.get("chapters")?.jsonArray
            ?: props?.get("series")?.jsonObject?.get("chapters")?.jsonArray
            ?: return emptyList()

        return chaptersArray.map { jsonElement ->
            val obj = jsonElement.jsonObject
            SChapter.create().apply {
                name = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Chapter"
                // Slug is usually used for URL construction
                val slug = obj["slug"]?.jsonPrimitive?.contentOrNull
                val seriesSlug = props["series"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull
                
                if (slug != null && seriesSlug != null) {
                     url = "/komik/$seriesSlug/$slug"
                } else {
                     // Fallback if URL structure differs
                     url = ""
                }

                date_upload = parseDate(obj["created_at"]?.jsonPrimitive?.contentOrNull)
            }
        }.reversed()
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val nextData = getNextData(document)
        val props = nextData["props"]?.jsonObject?.get("pageProps")?.jsonObject
        
        val images = props?.get("chapter")?.jsonObject?.get("images")?.jsonArray
            ?: props?.get("data")?.jsonObject?.get("images")?.jsonArray
            ?: return emptyList()

        return images.mapIndexed { index, element ->
            val url = element.jsonPrimitive.content
            Page(index, "", url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    // ============================== Helpers ===============================
    private fun getNextData(document: Document): JsonObject {
        val nextData = document.selectFirst("script[id=__NEXT_DATA__]")?.data()
            ?: throw Exception("Could not find __NEXT_DATA__ script")
        return json.parseToJsonElement(nextData).jsonObject
    }
}
EOF

# Success Message
echo -e "\033[0;32mExtension created successfully! Build with: ./gradlew :src:id:softkomik:assembleDebug\033[0m"
