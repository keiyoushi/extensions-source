package eu.kanade.tachiyomi.extension.all.silo

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.zip.dataRange
import keiyoushi.zip.range
import keiyoushi.zip.readEntry
import keiyoushi.zip.zipDirectory
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import java.io.IOException
import java.util.Locale

@Source
abstract class Silo :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val apiKey: String
        get() = preferences.getString(PREF_API_KEY, "")!!.trim()

    private val profilePref: String
        get() = preferences.getString(PREF_PROFILE, "")!!.trim()

    private val libraryId: String
        get() = preferences.getString(PREF_LIBRARY, "")!!.trim()

    private val api: String
        get() = "$baseUrl/api/v2"

    @Volatile
    private var primaryProfileId: String? = null

    // Silo pages with cursors; Mihon asks for page numbers, so remember the cursor for the next page.
    private class NextCursor(val key: String, val page: Int, val cursor: String)

    @Volatile
    private var nextCursor: NextCursor? = null

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "Mihon-Silo")
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::authInterceptor)
        addInterceptor(::zipRangeInterceptor)
    }

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = apiKey
        if (key.isEmpty()) throw IOException("Set the API key in the source settings")

        val builder = request.newBuilder().header("Authorization", "Bearer $key")
        if (!request.url.encodedPath.endsWith("/profiles")) {
            builder.header("X-Profile-Id", profileId(chain, key))
        }
        return chain.proceed(builder.build())
    }

    private fun profileId(chain: Interceptor.Chain, key: String): String {
        profilePref.takeIf { it.isNotEmpty() }?.let { return it }
        primaryProfileId?.let { return it }

        val request = Request.Builder()
            .url("$api/profiles")
            .header("Authorization", "Bearer $key")
            .build()
        val profiles = chain.proceed(request).use { response ->
            if (!response.isSuccessful) throw IOException("Silo rejected the API key (HTTP ${response.code})")
            response.parseAs<ProfilesDto>().items
        }
        val id = (profiles.firstOrNull { it.isPrimary } ?: profiles.firstOrNull())?.id
            ?: throw IOException("This Silo account has no profiles")
        primaryProfileId = id
        return id
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "", "title")

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, "", "-added_at")

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = browse(page, query, "title")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    private suspend fun browse(page: Int, query: String, sort: String): MangasPage {
        if (baseUrl.isBlank()) throw IOException("Set the server URL in the source settings")

        val url = "$api/catalog".toHttpUrl().newBuilder()
            .addQueryParameter("type", "manga")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", sort)
            .addQueryParameter("skip_total", "true")
        if (libraryId.isNotEmpty()) url.addQueryParameter("library_id", libraryId)
        if (query.isNotBlank()) url.addQueryParameter("q", query)

        val key = url.build().toString()
        if (page > 1) {
            val next = nextCursor?.takeIf { it.key == key && it.page == page }
                ?: return MangasPage(emptyList(), false)
            url.addQueryParameter("cursor", next.cursor)
        }

        val catalog = client.get(url.build(), Fetch.XHR).parseAs<CatalogDto>()
        val cursor = catalog.page?.nextCursor
        nextCursor = cursor?.let { NextCursor(key, page + 1, it) }

        return MangasPage(catalog.items.map { it.toSManga(baseUrl) }, catalog.page?.hasMore == true)
    }

    // Details and chapters
    override fun getMangaUrl(manga: SManga) = baseUrl

    // The browse list already carries every detail field, so there is nothing more to fetch for details.
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { manga },
        chapters = { if (fetchChapters) getChapterList(manga) else chapters },
    )

    private suspend fun getChapterList(manga: SManga): List<SChapter> = client
        .get("$api/catalog/items/${manga.url}/manga-files", Fetch.XHR)
        .parseAs<ChapterListDto>()
        .toSChapters()

    // Pages
    override fun getChapterUrl(chapter: SChapter) = baseUrl

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val fileId = client.get("$api/catalog/items/${chapter.url}/versions", Fetch.XHR)
            .parseAs<VersionsDto>().items.firstOrNull()?.fileId
            ?: return emptyList()

        val archiveUrl = "$api/ebooks/${chapter.url}/files/$fileId/read"
        return client.zipDirectory(archiveUrl).entries
            .filter { isImage(it.name) }
            .sortedWith { a, b -> CaseInsensitiveSimpleNaturalComparator.getInstance<String>().compare(a.name, b.name) }
            .mapIndexed { index, entry ->
                // The entry's location goes in the fragment so each page needs no extra directory read
                val fragment = "${entry.name}|${entry.method}|${entry.compressedSize}|${entry.localHeaderOffset}|${entry.localHeaderAllowance}"
                val pageUrl = archiveUrl.toHttpUrl().newBuilder().fragment(fragment).build().toString()
                Page(index, url = pageUrl, imageUrl = pageUrl)
            }
    }

    // The app adds its own resume Range to image requests, so the entry's range is set on the request itself
    protected override fun getImageRequest(page: Page): Request {
        val request = super.getImageRequest(page)
        val entry = request.url.zipEntryOrNull() ?: return request
        return request.newBuilder().range(entry.dataRange).build()
    }

    private fun zipRangeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val entry = request.url.zipEntryOrNull() ?: return chain.proceed(request)

        val rangeRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .range(entry.dataRange)
            .build()

        val response = chain.proceed(rangeRequest)
        if (!response.isSuccessful) return response

        val source = try {
            readEntry(response.body.source(), entry.compressedSize, entry.method)
        } catch (e: Throwable) {
            response.close()
            throw e
        }

        return response.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .body(source.buffer().asResponseBody(mediaType(entry.name).toMediaType()))
            .build()
    }

    private fun isImage(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return !lower.startsWith("__macosx") && !lower.substringAfterLast('/').startsWith(".") &&
            IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun mediaType(name: String): String = when (name.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        else -> "image/jpeg"
    }

    // Settings
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_API_KEY
            title = "API key"
            summary = "Created in Silo for the account that owns the libraries"
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PROFILE
            title = "Profile ID (optional)"
            summary = "Empty uses the account's primary profile"
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_LIBRARY
            title = "Library ID (optional)"
            summary = "Empty shows every manga library"
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_API_KEY = "api_key"
        private const val PREF_PROFILE = "profile_id"
        private const val PREF_LIBRARY = "library_id"
        private const val PAGE_SIZE = 50
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif")
    }
}

/** The zip entry a page's URL fragment describes, or null when the URL isn't a chapter page. */
private fun HttpUrl.zipEntryOrNull(): keiyoushi.zip.Entry? {
    if (!encodedPath.endsWith("/read")) return null
    val parts = fragment?.split("|") ?: return null
    if (parts.size < 5) return null
    return try {
        keiyoushi.zip.Entry(
            name = parts.dropLast(4).joinToString("|"),
            method = parts[parts.size - 4].toInt(),
            compressedSize = parts[parts.size - 3].toLong(),
            localHeaderOffset = parts[parts.size - 2].toLong(),
            localHeaderAllowance = parts[parts.size - 1].toInt(),
        )
    } catch (_: NumberFormatException) {
        null
    }
}
