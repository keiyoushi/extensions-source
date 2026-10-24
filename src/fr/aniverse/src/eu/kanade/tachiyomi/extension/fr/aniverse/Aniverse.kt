package eu.kanade.tachiyomi.extension.fr.aniverse

import android.util.Log
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Aniverse :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val readTracker by lazy { ReadTracker(preferences) }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getPopularManga(page: Int): MangasPage = searchManga(page, "", getFilterList())

    // Ordered by latest chapter release.
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val dto = client.get(url).parseAs<MangaListDto>()
        return MangasPage(dto.items.map { it.toSManga() }, dto.hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchManga(page, query, filters)

    private suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/anime/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "manga")
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query.trim())
                filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            }
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", "40")
            .build()
        val dto = client.get(url).parseAs<SearchDto>()
        return MangasPage(dto.data.map { it.toSManga() }, dto.hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = fetchMangaPage(manga.url)
        return SMangaUpdate(
            manga = dto.manga.toSManga(),
            chapters = dto.chapters
                .filterNot { it.isLocked }
                .map { it.toSChapter(dto.manga.slug) }
                .reversed(),
        )
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    private suspend fun fetchMangaPage(slug: String): MangaPageDto = client.get("$baseUrl/manga/$slug").extractNextJs<MangaPageDto>()
        ?: throw Exception("Impossible d'extraire les données du manga")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() !in listOf("manga", "read")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return fetchMangaPage(slug).manga.toSManga()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Must run before the first suspension point, while the caller is still on the stack.
        val fromReader = isCalledFromReader()

        val response = client.get(getChapterUrl(chapter))
        // Locked chapters redirect to the sign-in page.
        if (response.request.url.encodedPath.startsWith("/sign-in")) {
            response.close()
            return emptyList()
        }

        val reader = response.extractNextJs<ReaderDto>()
            ?: throw Exception("Impossible d'extraire la liste des pages")
        if (reader.locked) return emptyList()

        if (fromReader && preferences.getBoolean(SYNC_PREF_KEY, true)) {
            reportReading(reader, chapter.url.substringAfterLast('/'))
        }

        return reader.pages.mapIndexed { index, page -> Page(index, imageUrl = page.src) }
    }

    // Mihon only asks for page lists from its reader and its downloader; downloads aren't reading.
    private fun isCalledFromReader() = Thread.currentThread().stackTrace.any { it.className.startsWith(READER_PACKAGE) }

    // Sends what the site's own reader sends for a chapter being read.
    private fun reportReading(reader: ReaderDto, chapterId: String) {
        val loaded = reader.indexOf(chapterId).takeIf { it >= 0 } ?: return
        val reading = readTracker.onReaderLoad(reader.slug, loaded) ?: return

        val history = HistorySyncRequestDto(listOf(HistoryChangeDto("manga", reader.toHistoryEntry(reading))))
        val progress = ProgressRequestDto(reader.chapterIdAt(reading))
        val label = reader.chapterLabelAt(reading)
        syncScope.launch {
            // Reading still works when this fails, only the sync is skipped.
            val error = try {
                val codes = listOf(
                    client.post("$baseUrl/api/history/sync", headers, history.toJsonRequestBody(), ensureSuccess = false),
                    client.post("$baseUrl/api/manga/progress", headers, progress.toJsonRequestBody(), ensureSuccess = false),
                ).map { response -> response.use { it.code } }
                when {
                    codes.all { it in 200..299 } -> null
                    401 in codes -> "non connecté"
                    else -> "erreur du site (${codes.first { it !in 200..299 }})"
                }
            } catch (e: Exception) {
                Log.w(name, "Account sync failed", e)
                "pas de connexion internet"
            }
            preferences.edit().apply {
                if (error == null) {
                    putString(LAST_SYNC_KEY, label)
                    putLong(LAST_SYNC_TIME_KEY, System.currentTimeMillis())
                }
                putString(LAST_SYNC_ERROR_KEY, error)
            }.apply()
        }
    }

    private suspend fun fetchAccountStatus(): String = try {
        val user = client.post("$baseUrl/api/user/me", headers, EMPTY_BODY).parseAs<UserMeDto>().user
        if (user == null) {
            "Non connecté : ouvrez la source dans la WebView et connectez-vous sur le site."
        } else {
            "Connecté en tant que ${user.displayName}"
        }
    } catch (e: Exception) {
        Log.w(name, "Account check failed", e)
        "Impossible de vérifier la connexion (pas de connexion internet ?)"
    }

    private fun accountSummary(account: String) = buildString {
        append(account)
        preferences.getString(LAST_SYNC_KEY, null)?.let { label ->
            val time = Instant.ofEpochMilli(preferences.getLong(LAST_SYNC_TIME_KEY, 0)).atZone(ZoneId.systemDefault())
            append("\nDernière synchro : ").append(label).append(" · ").append(SYNC_TIME_FORMAT.format(time))
        }
        preferences.getString(LAST_SYNC_ERROR_KEY, null)?.let { append("\nDernier envoi échoué : ").append(it) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        KindFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Extensions can't build a plain Preference, so the account status lives in the switch summary.
        val syncPref = SwitchPreferenceCompat(screen.context).apply {
            key = SYNC_PREF_KEY
            title = "Synchroniser la lecture avec le compte Aniverse"
            summary = syncSummary("Vérification de la connexion…")
            setDefaultValue(true)
        }.also(screen::addPreference)

        syncScope.launch {
            val account = fetchAccountStatus()
            withContext(Dispatchers.Main) { syncPref.summary = syncSummary(account) }
        }
    }

    private fun syncSummary(account: String) = buildString {
        append(accountSummary(account))
        append("\n\nLes chapitres ouverts dans le lecteur comptent dans l'historique et les quêtes du site. ")
        append("Connectez-vous d'abord sur le site via la WebView de la source. Les téléchargements ne sont pas comptés.")
    }

    private companion object {
        const val SYNC_PREF_KEY = "account_sync"
        const val LAST_SYNC_KEY = "last_sync"
        const val LAST_SYNC_TIME_KEY = "last_sync_time"
        const val LAST_SYNC_ERROR_KEY = "last_sync_error"
        const val READER_PACKAGE = "eu.kanade.tachiyomi.ui.reader."

        val EMPTY_BODY = ByteArray(0).toRequestBody()
        val SYNC_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm", Locale.FRENCH)
    }
}
