package eu.kanade.tachiyomi.extension.all.stashapp

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class StashApp :
    HttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val name = "StashApp"

    override val lang = "all"

    override val supportsLatest = true

    override val baseUrl: String
        get() = preferences.getString(BASE_URL_KEY, BASE_URL)!!.trim()

    // https://github.com/stashapp/stash/blob/v0.30.1/pkg/sqlite/gallery.go#L773
    private fun getBriefMangaRequest(page: Int, q: String, sort: String, direction: String): Request {
        val variables = JSONObject()
            .put(
                "filter",
                JSONObject()
                    .put("q", q)
                    .put("page", page)
                    .put("per_page", BRIEF_MANGA_PER_PAGE)
                    .put("sort", sort)
                    .put("direction", direction),
            )

        val query = FIND_GALLERIES_QUERY.trimIndent()

        val body = JSONObject()
            .put("query", query)
            .put("operationName", "FindGalleries")
            .put("variables", variables)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        return Request.Builder()
            .url("$baseUrl/graphql")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/graphql-response+json, application/json")
            .post(body)
            .build()
    }

    private fun parseBriefManga(response: Response): MangasPage {
        val galleries = response.body.string()
            .let(::JSONObject)
            .optJSONObject("data")
            ?.optJSONObject("findGalleries")
            ?.optJSONArray("galleries")
            ?: return MangasPage(emptyList(), false)

        val mangas = buildList {
            for (index in 0 until galleries.length()) {
                val gallery = galleries.optJSONObject(index) ?: continue
                val id = gallery.optString("id")
                val title = gallery.optString("title").takeIf { it.isNotBlank() }
                    ?: gallery.optJSONObject("folder")
                        ?.optString("path")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::pathLast)

                if (id.isBlank() || title.isNullOrBlank()) continue

                add(
                    SManga.create().apply {
                        url = toAbsoluteUrl("/galleries/$id")
                        this.title = title
                        thumbnail_url = gallery
                            .optJSONObject("cover")
                            ?.toThumbnailUrl()
                    },
                )
            }
        }

        return MangasPage(
            mangas = mangas,
            hasNextPage = galleries.length() >= BRIEF_MANGA_PER_PAGE && mangas.isNotEmpty(),
        )
    }

    override fun popularMangaRequest(page: Int) = getBriefMangaRequest(page, "", "rating", "DESC")

    override fun popularMangaParse(response: Response): MangasPage = parseBriefManga(response)

    // TODO support getFilterList
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = getBriefMangaRequest(page, query, "path", "ASC")

    override fun searchMangaParse(response: Response): MangasPage = parseBriefManga(response)

    override fun latestUpdatesRequest(page: Int): Request = getBriefMangaRequest(page, "", "updated_at", "DESC")

    override fun latestUpdatesParse(response: Response): MangasPage = parseBriefManga(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val variables = JSONObject().put("id", urlLast(manga.url))

        val body = JSONObject()
            .put("query", FIND_GALLERY_QUERY1.trimIndent())
            .put("operationName", "FindGallery")
            .put("variables", variables)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        return Request.Builder()
            .url("$baseUrl/graphql")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/graphql-response+json, application/json")
            .post(body)
            .build()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val gallery = response.body.string()
            .let(::JSONObject)
            .optJSONObject("data")
            ?.optJSONObject("findGallery")
            ?: return SManga.create()

        val id = gallery.optString("id")
        val title = gallery.optString("title").takeIf { it.isNotBlank() }
            ?: gallery.optJSONObject("folder")
                ?.optString("path")
                ?.takeIf { it.isNotBlank() }
                ?.let(::pathLast)
            ?: id

        val tags = gallery.optJSONArray("tags")
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val name = array.optJSONObject(index)?.optString("name").orEmpty()
                        if (name.isNotBlank()) add(name)
                    }
                }
            }
            .orEmpty()
            .joinToString(", ")

        return SManga.create().apply {
            url = toAbsoluteUrl("/galleries/$id")
            this.title = title
            artist = gallery.optString("photographer").takeIf { it.isNotBlank() }
            author = artist
            description = gallery.optString("details").takeIf { it.isNotBlank() }
            genre = tags.takeIf { it.isNotBlank() }
            status = SManga.UNKNOWN
            thumbnail_url = gallery
                .optJSONObject("cover")
                ?.toThumbnailUrl()
            update_strategy = UpdateStrategy.ALWAYS_UPDATE
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val variables = JSONObject().put("id", urlLast(manga.url))

        val body = JSONObject()
            .put("query", FIND_GALLERY_QUERY2.trimIndent())
            .put("operationName", "FindGallery")
            .put("variables", variables)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        return Request.Builder()
            .url("$baseUrl/graphql")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/graphql-response+json, application/json")
            .post(body)
            .build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val gallery = response.body.string()
            .let(::JSONObject)
            .optJSONObject("data")
            ?.optJSONObject("findGallery")
            ?: return emptyList()

        val id = gallery.optString("id")
        val createdAt = gallery.optString("created_at").takeIf { it.isNotBlank() }

        return listOf(
            SChapter.create().apply {
                url = toAbsoluteUrl("/galleries/$id")
                name = "Chapter"
                date_upload = createdAt?.let(::parseIsoDatetimeMillis) ?: 0L
                chapter_number = 1f
                scanlator = gallery.optString("photographer").takeIf { it.isNotBlank() }
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val variables = JSONObject().put("id", urlLast(chapter.url).toInt())

        val body = JSONObject()
            .put("query", FIND_GALLERY_IMAGES_QUERY.trimIndent())
            .put("operationName", "FindGallery")
            .put("variables", variables)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        return Request.Builder()
            .url("$baseUrl/graphql")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/graphql-response+json, application/json")
            .post(body)
            .build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val images = response.body.string()
            .let(::JSONObject)
            .optJSONObject("data")
            ?.optJSONObject("findImages")
            ?.optJSONArray("images")
            ?: return emptyList()

        return buildList {
            for (index in 0 until images.length()) {
                val imageId = images.optJSONObject(index)?.optString("id").orEmpty()
                if (imageId.isBlank()) continue
                val url = toAbsoluteUrl("/image/$imageId/image")

                add(Page(index = size, url = url, imageUrl = url))
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/*")
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    // TODO override getFilterList

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_KEY
            title = "Server URL"
            summary = "Example: http://localhost:9999"
            setDefaultValue(BASE_URL)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("baseUrl", newValue as String).commit()
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val BASE_URL_KEY = "base_url"
        private const val BASE_URL = "http://localhost:9999"
        private const val BRIEF_MANGA_PER_PAGE = 25
        private const val FIND_GALLERIES_QUERY = """
            query FindGalleries(${"$"}filter: FindFilterType) {
                findGalleries(filter: ${"$"}filter) {
                    galleries {
                        id
                        title
                        folder {
                            path
                        }
                        cover {
                            paths {
                                thumbnail
                            }
                            visual_files {
                                __typename
                            }
                        }
                    }
                }
            }
        """
        private const val FIND_GALLERY_QUERY1 = """
            query FindGallery(${'$'}id: ID!) {
                findGallery(id: ${'$'}id) {
                      id
                      title
                      folder {
                          path
                      }
                      photographer
                      details
                      tags {
                          name
                      }
                      cover {
                          paths {
                              thumbnail
                          }
                          visual_files {
                              __typename
                          }
                      }
                }
            }
        """
        private const val FIND_GALLERY_QUERY2 = """
            query FindGallery(${'$'}id: ID!) {
                findGallery(id: ${'$'}id) {
                    id
                    created_at
                    photographer
                }
            }
        """

        private const val FIND_GALLERY_IMAGES_QUERY = """
            query FindGallery(${'$'}id: Int!) {
                findImages(
                    filter: { per_page: -1, sort: "path" }
                    image_filter: {
                        galleries_filter: { id: { value: ${'$'}id, modifier: EQUALS } }
                        files_filter: { image_file_filter: { format: { value: "", modifier: NOT_EQUALS } } }
                    }
                ) {
                    images {
                        id
                    }
                }
            }
        """
    }

    private fun toAbsoluteUrl(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> "$baseUrl$path"
        else -> "$baseUrl/$path"
    }

    private fun urlLast(url: String): String = url.substringBefore('?')
        .substringBefore('#')
        .let(::pathLast)

    private fun pathLast(path: String): String = path.trimEnd('/', '\\')
        .substringAfterLast('/')
        .substringAfterLast('\\')

    // paths {
    //     thumbnail
    // }
    // visual_files {
    //     __typename
    // }
    private fun JSONObject.toThumbnailUrl(): String? {
        val firstVisualFileType = optJSONArray("visual_files")
            ?.optJSONObject(0)
            ?.optString("__typename")

        if (firstVisualFileType != "ImageFile") return null

        return optJSONObject("paths")
            ?.optString("thumbnail")
            ?.takeIf { it.isNotBlank() }
            ?.let(::toAbsoluteUrl)
    }

    private fun parseIsoDatetimeMillis(value: String): Long {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }
}
