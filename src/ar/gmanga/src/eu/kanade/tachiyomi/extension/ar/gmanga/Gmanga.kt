package eu.kanade.tachiyomi.extension.ar.gmanga

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_CHAPTER_LISTING
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_CHAPTER_LISTING_SHOW_ALL
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_CHAPTER_LISTING_SHOW_POPULAR
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_LASTETS_LISTING
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_LASTETS_LISTING_SHOW_LASTETS_MANGA
import eu.kanade.tachiyomi.extension.ar.gmanga.dto.TableDto
import eu.kanade.tachiyomi.extension.ar.gmanga.dto.asChapterList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Gmanga : ConfigurableSource, HttpSource() {

    private val domain: String = "gmanga.org"

    override val baseUrl: String = "https://$domain"

    override val lang: String = "ar"

    override val name: String = "GMANGA"

    override val supportsLatest: Boolean = true

    private val json: Json by injectLazy()

    private val preferences = GmangaPreferences(id)

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(4)
        .build()

    private val parsedDatePattern: SimpleDateFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss ZZZ zzz",
        Locale.ENGLISH,
    )
    private val formattedDatePattern: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", USER_AGENT)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        preferences.setupPreferenceScreen(screen)

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/mangas/$mangaId/releases", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = decryptResponse(response)

        val table = json.decodeFromJsonElement<TableDto>(data)
        val chapterList = table.asChapterList()

        val releases = when (preferences.getString(PREF_CHAPTER_LISTING)) {
            PREF_CHAPTER_LISTING_SHOW_POPULAR ->
                chapterList.releases
                    .groupBy { release -> release.chapterizationId }
                    .mapNotNull { (_, releases) -> releases.maxByOrNull { it.views } }
            PREF_CHAPTER_LISTING_SHOW_ALL -> chapterList.releases
            else -> emptyList()
        }

        return releases.map { release ->
            SChapter.create().apply {
                val chapter = chapterList.chapters.first { it.id == release.chapterizationId }
                val team = chapterList.teams.firstOrNull { it.id == release.teamId }

                url = "/r/${release.id}"
                chapter_number = chapter.chapter
                date_upload = release.timestamp * 1000
                scanlator = team?.name

                val chapterName = chapter.title.let { if (it.trim() != "") " - $it" else "" }
                name = "${chapter_number.let { if (it % 1 > 0) it else it.toInt() }}$chapterName"
            }
        }.sortedWith(compareBy({ -it.chapter_number }, { -it.date_upload }))
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val isLatest = when (preferences.getString(PREF_LASTETS_LISTING)) {
            PREF_LASTETS_LISTING_SHOW_LASTETS_MANGA -> true
            PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER -> false
            else -> true
        }

        val mangas = if (!isLatest) {
            val decMga = decryptResponse(response)
            val selectedManga = decMga["rows"]!!.jsonArray[0].jsonObject["rows"]!!.jsonArray
            buildJsonArray {
                for (i in 0 until selectedManga.size) {
                    add(selectedManga[i].jsonArray[17])
                }
            }
        } else {
            val data = json.decodeFromString<JsonObject>(
                response.asJsoup().select(".js-react-on-rails-component").html(),
            )
            data["mangaDataAction"]!!.jsonObject["newMangas"]!!.jsonArray
        }
        return MangasPage(
            mangas.jsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it.jsonObject["id"]!!.jsonPrimitive.content}"
                    title = it.jsonObject["title"]!!.jsonPrimitive.content
                    val thumbnail = "medium_${
                    it.jsonObject["cover"]!!.jsonPrimitive.content.substringBeforeLast(".")
                    }.webp"
                    thumbnail_url =
                        "https://media.gmanga.me/uploads/manga/cover/${it.jsonObject["id"]!!.jsonPrimitive.content}/$thumbnail"
                }
            },
            (mangas.size >= 30) && !isLatest,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val latestUrl = when (preferences.getString(PREF_LASTETS_LISTING)) {
            PREF_LASTETS_LISTING_SHOW_LASTETS_MANGA -> "$baseUrl/mangas/latest"
            PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER -> "https://api.gmanga.me/api/releases?page=$page"
            else -> "$baseUrl/mangas/latest"
        }
        return GET(latestUrl, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val altNamePrefix = "مسميّات أخرى"
        val translationStatusPrefix = "حالة الترجمة"
        val startedDayPrefix = "تاريخ النشر"
        val endedDayPrefix = "تاريخ الانتهاء"
        val data = json.decodeFromString<JsonObject>(
            response.asJsoup().select(".js-react-on-rails-component").html(),
        )
        val mangaData = data["mangaDataAction"]!!.jsonObject["mangaData"]!!.jsonObject
        return SManga.create().apply {
            description =
                mangaData["summary"]!!.jsonPrimitive.contentOrNull?.ifEmpty { "لم يتم اضافة قصة بعد" }
            artist =
                mangaData["artists"]!!.jsonArray.joinToString(", ") { it.jsonObject["name"]!!.jsonPrimitive.content }
            author =
                mangaData["authors"]!!.jsonArray.joinToString(", ") { it.jsonObject["name"]!!.jsonPrimitive.content }
            status = parseStatus(mangaData["story_status"].toString())
            genre = listOfNotNull(
                mangaData["type"]!!.jsonObject["title"]!!.jsonPrimitive.content,
                mangaData["type"]!!.jsonObject["name"]!!.jsonPrimitive.content,
                mangaData["categories"]!!.jsonArray.joinToString(", ") { it.jsonObject["name"]!!.jsonPrimitive.content },
            ).joinToString(", ")

            parseTranslationStatus(mangaData["translation_status"].toString()).let {
                description = "$description\n\n:$translationStatusPrefix ᗏ \n$it •"
            }
            var startedDate =
                mangaData["s_date"]!!.jsonPrimitive.content.takeIf { it.isBlank().not() }
            startedDate = if (startedDate.isNullOrBlank().not()) {
                parsedDatePattern.parse(startedDate!!)?.let { formattedDatePattern.format(it) }
            } else {
                null
            }
            var endedDay = mangaData["e_date"]!!.jsonPrimitive.content.takeIf { it.isBlank().not() }
            endedDay = if (endedDay.isNullOrBlank().not()) {
                parsedDatePattern.parse(endedDay!!)?.let { formattedDatePattern.format(it) }
            } else {
                null
            }

            val alternativeName = listOfNotNull(
                mangaData["synonyms"]!!.jsonPrimitive.content.takeIf { it.isBlank().not() },
                mangaData["arabic_title"]!!.jsonPrimitive.content.takeIf { it.isBlank().not() },
                mangaData["japanese"]!!.jsonPrimitive.content.takeIf { it.isBlank().not() },
                mangaData["english"]!!.jsonPrimitive.content.takeIf { it.isBlank().not() },
            ).joinToString("\n").trim()

            val additionalInformation = listOfNotNull(
                startedDate,
                endedDay,
                alternativeName,
            )
            additionalInformation.forEach { info ->
                when (info) {
                    startedDate ->
                        description =
                            "$description\n\n:$startedDayPrefix ᗏ \n$startedDate •"
                    endedDay -> description = "$description\n\n:$endedDayPrefix ᗏ \n$endedDay •"
                    alternativeName ->
                        description =
                            "$description\n\n:$altNamePrefix ᗏ \n$alternativeName •"
                    else -> description
                }
            }
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("2") -> SManga.ONGOING
        status.contains("3") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseTranslationStatus(status: String?) = when {
        status == null -> "مجهول"
        status.contains("0") -> "منتهية"
        status.contains("1") -> "مستمرة"
        status.contains("2") -> "متوقفة"
        else -> "مجهول"
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url.toString()
        val data = json.decodeFromString<JsonObject>(
            response.asJsoup().select(".js-react-on-rails-component").html(),
        )
        val releaseData =
            data["readerDataAction"]!!.jsonObject["readerData"]!!.jsonObject["release"]!!.jsonObject

        val hasWebP = releaseData["webp_pages"]!!.jsonArray.size > 0
        return releaseData[if (hasWebP) "webp_pages" else "pages"]!!.jsonArray.map { it.jsonPrimitive.content }
            .sortedWith(pageSort)
            .mapIndexed { index, pageUri ->
                Page(
                    index,
                    "$url#page_$index",
                    "https://media.gmanga.me/uploads/releases/${releaseData["storage_key"]!!.jsonPrimitive.content}/hq${if (hasWebP) "_webp" else ""}/$pageUri",
                )
            }
    }

    private val pageSort =
        compareBy<String>({ parseNumber(0, it) ?: Double.MAX_VALUE }, { parseNumber(1, it) }, { parseNumber(2, it) })

    private fun parseNumber(index: Int, string: String): Double? =
        Regex("\\d+").findAll(string).map { it.value }.toList().getOrNull(index)?.toDoubleOrNull()

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())

    override fun searchMangaParse(response: Response): MangasPage {
        val data = decryptResponse(response)
        val mangas = data["mangas"]!!.jsonArray
        return MangasPage(
            mangas.jsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it.jsonObject["id"]!!.jsonPrimitive.content}"
                    title = it.jsonObject["title"]!!.jsonPrimitive.content
                    val thumbnail = "medium_${
                    it.jsonObject["cover"]!!.jsonPrimitive.content.substringBeforeLast(".")
                    }.webp"
                    thumbnail_url =
                        "https://media.gmanga.me/uploads/manga/cover/${it.jsonObject["id"]!!.jsonPrimitive.content}/$thumbnail"
                }
            },
            mangas.size == 50,
        )
    }

    private fun decryptResponse(response: Response): JsonObject {
        val encryptedData =
            json.decodeFromString<JsonObject>(response.body.string())["data"]!!.jsonPrimitive.content
        val decryptedData = decrypt(encryptedData)
        return json.decodeFromString(decryptedData)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GmangaFilters.buildSearchPayload(
            page,
            query,
            if (filters.isEmpty()) getFilterList() else filters,
        ).let {
            val body = it.toString().toRequestBody(MEDIA_TYPE)
            POST("$baseUrl/api/mangas/search", headers, body)
        }
    }

    override fun getFilterList() = GmangaFilters.getFilterList()

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
