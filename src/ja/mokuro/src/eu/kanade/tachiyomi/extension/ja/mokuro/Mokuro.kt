package eu.kanade.tachiyomi.extension.ja.mokuro

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.zip.zipDirectoryAsync
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.URLDecoder
import java.net.URLEncoder

@Source
abstract class Mokuro :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val titleLang: String
        get() = preferences.getString(TITLE_LANG_PREF, TITLE_LANG_DEFAULT) ?: TITLE_LANG_DEFAULT

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(CbzInterceptor())
    }

    // ===============================
    // Popular
    // ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val catalog = getCatalog()
        val pref = titleLang
        return MangasPage(catalog.series.map { it.toSManga(pref) }, false)
    }

    // ===============================
    // Latest
    // ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val catalog = getCatalog()
        val pref = titleLang
        val mangas = catalog.series
            .sortedByDescending { it.updatedAt.orEmpty() }
            .map { it.toSManga(pref) }
        return MangasPage(mangas, false)
    }

    // ===============================
    // Search
    // ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val catalog = getCatalog()
        val pref = titleLang
        var sequence = catalog.series.asSequence()

        if (query.isNotBlank()) {
            sequence = sequence.filter { it.matches(query, pref) }
        }

        filters.forEach { filter ->
            when (filter) {
                is TagFilter -> {
                    val selected = filter.values[filter.state]
                    if (selected != "All") {
                        sequence = sequence.filter { it.tag?.contains(selected, ignoreCase = true) == true }
                    }
                }
                is SortFilter -> {
                    filter.state?.let { sort ->
                        sequence = if (sort.ascending) {
                            when (sort.index) {
                                0 -> sequence.sortedBy { it.displayTitle(pref).lowercase() }
                                1 -> sequence.sortedBy { it.updatedAt.orEmpty() }
                                else -> sequence
                            }
                        } else {
                            when (sort.index) {
                                0 -> sequence.sortedByDescending { it.displayTitle(pref).lowercase() }
                                1 -> sequence.sortedByDescending { it.updatedAt.orEmpty() }
                                else -> sequence.toList().asReversed().asSequence()
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        return MangasPage(sequence.map { it.toSManga(pref) }.toList(), false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        TagFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = url.host
        val seriesTitle: String? = when {
            host == baseUrl.toHttpUrl().host -> {
                url.fragment?.takeIf { it.isNotEmpty() }
                    ?: if (url.pathSegments.firstOrNull() == "mokuro-reader") url.pathSegments.getOrNull(1) else null
            }
            host == "reader.mokuro.app" -> {
                val fragment = url.fragment ?: ""
                val cbzParam = fragment.substringAfter("cbz=", "").substringBefore("&").takeIf { it.isNotEmpty() }
                    ?: url.queryParameter("cbz")
                cbzParam?.let { raw ->
                    val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                    val cbzHttpUrl = decoded.toHttpUrlOrNull()
                    if (cbzHttpUrl != null && cbzHttpUrl.pathSegments.firstOrNull() == "mokuro-reader") {
                        cbzHttpUrl.pathSegments.getOrNull(1)
                    } else {
                        null
                    }
                }
            }
            else -> null
        }

        if (seriesTitle.isNullOrEmpty()) return null

        val decodedTitle = runCatching { URLDecoder.decode(seriesTitle, "UTF-8") }.getOrDefault(seriesTitle)
        val catalog = getCatalog()
        val entry = catalog.series.find {
            it.seriesTitle.equals(seriesTitle, ignoreCase = true) ||
                it.seriesTitle.equals(decodedTitle, ignoreCase = true)
        } ?: return null

        return entry.toSManga(titleLang)
    }

    // ===============================
    // Details & Chapters
    // ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            val catalog = getCatalog()
            val catalogEntry = catalog.series.find { it.seriesTitle == manga.url }
                ?: throw Exception("Series not found")

            manga.apply {
                catalogEntry.fillDetails(this, titleLang)
            }
        } else {
            manga
        }

        val chapterList = if (fetchChapters) {
            val detail = getSeriesDetail(manga.url)
            detail.volumes.asReversed().map { it.toSChapter(detail.seriesTitle) }
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override fun getMangaUrl(manga: SManga): String {
        val encoded = URLEncoder.encode(manga.url, "UTF-8").replace("+", "%20")
        return "$baseUrl/catalog#$encoded"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (seriesPath, volumeName) = chapter.url.split("|", limit = 2)

        val cbzUrl = "$baseUrl/mokuro-reader".toHttpUrl().newBuilder()
            .addPathSegment(seriesPath)
            .addPathSegment("$volumeName.cbz")
            .build()
            .toString()

        val encodedUrl = URLEncoder.encode(cbzUrl, "UTF-8")

        return "https://reader.mokuro.app/#/upload?cbz=$encodedUrl"
    }

    // ===============================
    // Pages
    // ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesPath, volumeName) = chapter.url.split("|", limit = 2)
        val url = "$baseUrl/mokuro-reader".toHttpUrl().newBuilder()
            .addPathSegment(seriesPath)
            .addPathSegment("$volumeName.mokuro")
            .build()

        val response = client.get(url, headers)
        return response.use { resp ->
            val mokuro = resp.parseAs<MokuroDto>()
            val cbzUrl = resp.request.url.newBuilder()
                .encodedPath(resp.request.url.encodedPath.removeSuffix(".mokuro") + ".cbz")
                .build()

            val byName = client.zipDirectoryAsync(cbzUrl.toString(), headers).entries.associateBy { it.name }

            mokuro.pages.mapIndexed { index, page ->
                val entry = byName[page.imgPath] ?: throw Exception("Entry not found in CBZ: ${page.imgPath}")
                val data = ImageRequest(
                    page.imgPath,
                    entry.localHeaderOffset,
                    entry.compressedSize,
                    entry.method,
                ).toJsonString()
                Page(index, imageUrl = "$cbzUrl#$data")
            }
        }
    }

    // ===============================
    // Preferences
    // ===============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = TITLE_LANG_PREF
            title = "Display title language"
            summary = "%s"
            entries = arrayOf("Native", "English", "Romaji", "Folder name")
            entryValues = arrayOf("native", "english", "romaji", "folder")
            setDefaultValue(TITLE_LANG_DEFAULT)
        }.also(screen::addPreference)
    }

    // ===============================
    // Helpers
    // ===============================

    private suspend fun getCatalog(): CatalogDto {
        val response = client.get("$baseUrl/mokuro-reader/catalog.json")
        return response.parseAs<CatalogDto>()
    }

    private suspend fun getSeriesDetail(seriesTitle: String): SeriesDetailDto {
        val url = "$baseUrl/mokuro-reader".toHttpUrl().newBuilder()
            .addPathSegment(seriesTitle)
            .addPathSegment("series.json")
            .build()

        val response = client.get(url)
        return response.parseAs<SeriesDetailDto>()
    }

    companion object {
        private const val TITLE_LANG_PREF = "mokuro_title_lang"
        private const val TITLE_LANG_DEFAULT = "native"
    }
}
