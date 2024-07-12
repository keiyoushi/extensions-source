package eu.kanade.tachiyomi.extension.en.koharu

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty1

class Koharu : HttpSource(), ConfigurableSource {
    override val name = "Koharu"

    override val baseUrl = "https://koharu.to"

    private val apiUrl = baseUrl.replace("://", "://api.")

    private val apiBooksUrl = "$apiUrl/books"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var quality = preferences.getString(PREF_IMAGERES, "0")!!

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    private fun getManga(book: Entry) = SManga.create().apply {
        setUrlWithoutDomain("${book.id}/${book.public_key}")
        title = book.title
        thumbnail_url = book.thumbnail.path
    }

    private fun getImagesByMangaEntry(entry: MangaEntry): ImagesInfo {
        val dataKey = readInstanceProperty(entry.data, quality) as DataKey

        val imagesResponse = client.newCall(POST("$apiBooksUrl/data/${entry.id}/${entry.public_key}/${dataKey.id}/${dataKey.public_key}", headers)).execute()
        val images = imagesResponse.parseAs<ImagesInfo>()
        return images
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$apiBooksUrl?page=$page", headers)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$apiBooksUrl?sort=6&page=$page", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Books>()

        return MangasPage(data.entries.map(::getManga), data.page * data.limit < data.total)
    }

    // Search

    override fun getFilterList(): FilterList = getFilters()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiBooksUrl.toHttpUrl().newBuilder().apply {
            val terms = mutableListOf(query.trim())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.getValue())

                    is CategoryFilter -> {
                        val activeFilter = filter.state.filter { it.state }
                        if (activeFilter.isNotEmpty()) {
                            addQueryParameter("cat", activeFilter.sumOf { it.value }.toString())
                        }
                    }

                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            terms += filter.state.split(",").filter(String::isNotBlank).map { tag ->
                                val trimmed = tag.trim()
                                buildString {
                                    if (trimmed.startsWith('-')) {
                                        append("-")
                                    }
                                    append(filter.type)
                                    append("!:")
                                    append("\"")
                                    append(trimmed.lowercase().removePrefix("-"))
                                    append("\"")
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("s", "title:\"$query\" " + terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiBooksUrl/detail/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaEntry>().toSManga()
    }

    private val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
    private fun MangaEntry.toSManga() = SManga.create().apply {
        val artists = mutableListOf<String>()
        val circles = mutableListOf<String>()
        val parodies = mutableListOf<String>()
        val magazines = mutableListOf<String>()
        val characters = mutableListOf<String>()
        val cosplayers = mutableListOf<String>()
        val females = mutableListOf<String>()
        val males = mutableListOf<String>()
        val mixed = mutableListOf<String>()
        val other = mutableListOf<String>()
        // val languages = mutableListOf<String>()
        val uploaders = mutableListOf<String>()
        val tags = mutableListOf<String>()
        for (tag in this@toSManga.tags) {
            when (tag.namespace) {
                1 -> artists.add(tag.name)
                2 -> circles.add(tag.name)
                3 -> parodies.add(tag.name)
                4 -> magazines.add(tag.name)
                5 -> characters.add(tag.name)
                6 -> cosplayers.add(tag.name)
                7 -> uploaders.add(tag.name)
                8 -> males.add(tag.name + " ♂")
                9 -> females.add(tag.name + " ♀")
                10 -> mixed.add(tag.name + " ◊")
                // 11 -> languages.add(tag.name)
                12 -> other.add(tag.name + " ◊")
                else -> tags.add(tag.name + " ◊")
            }
        }
        author = (circles.emptyToNull() ?: artists).joinToString()
        artist = artists.joinToString()
        genre = (tags + males + females + mixed).joinToString()
        description = buildString {
            circles.emptyToNull()?.joinToString()?.let {
                append("Circles: ", it, "\n")
            }
            uploaders.emptyToNull()?.joinToString()?.let {
                append("Uploaders: ", it, "\n")
            }
            magazines.emptyToNull()?.joinToString()?.let {
                append("Magazines: ", it, "\n")
            }
            cosplayers.emptyToNull()?.joinToString()?.let {
                append("Cosplayers: ", it, "\n")
            }
            parodies.emptyToNull()?.joinToString()?.let {
                append("Parodies: ", it, "\n")
            }
            characters.emptyToNull()?.joinToString()?.let {
                append("Characters: ", it, "\n")
            }
            append("Pages: ", thumbnails.entries.size, "\n\n")

            try {
                append("Added: ", dateReformat.format(((updated_at ?: created_at))), "\n")
            } catch (_: Exception) {}
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun <T> Collection<T>.emptyToNull(): Collection<T>? {
        return this.ifEmpty { null }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/g/${manga.url}"

    // Chapter

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$apiBooksUrl/detail/${manga.url}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<MangaEntry>()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = "${manga.id}/${manga.public_key}"
                date_upload = (manga.updated_at ?: manga.created_at)
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g/${chapter.url}"

    // Page List

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$apiBooksUrl/detail/${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val mangaEntry = response.parseAs<MangaEntry>()
        val imagesInfo = getImagesByMangaEntry(mangaEntry)

        return imagesInfo.entries.mapIndexed { i, image ->
            Page(
                i,
                imageUrl = "${imagesInfo.base}/${image.path}",
            )
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Settings

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGERES
            title = "Image Resolution"
            entries = arrayOf("780x", "980x", "1280x", "1600x", "Original")
            entryValues = arrayOf("780", "980", "1280", "1600", "0")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                quality = newValue.toString()
                true
            }
        }.also(screen::addPreference)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readInstanceProperty(instance: Data, propertyName: String): Any? {
        val property = instance::class.members.first { it.name == propertyName } as KProperty1<Any, *>
        return property.get(instance)
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    companion object {
        private const val PREF_IMAGERES = "0"
    }
}
