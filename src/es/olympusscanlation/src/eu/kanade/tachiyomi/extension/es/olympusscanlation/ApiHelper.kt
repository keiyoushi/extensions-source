package eu.kanade.tachiyomi.extension.es.olympusscanlation

import android.util.Log
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.injectLazy

class ApiHelper(
    private val client: OkHttpClient,
    private val headers: Map<String, String>,
    private val apiBaseUrl: String,
) {
    private val json: Json by injectLazy()
    private val websiteBaseUrl = apiBaseUrl
        .replace("https://dashboard.", "https://")
        .replace("https://panel.", "https://")

    fun resolveMangaByName(
        title: String,
        currentId: String? = null,
        cacheManager: MangaCacheManager,
    ): MangaDto? {
        val query = title.trim()
        if (query.length < 3) return null

        Log.d("OlympusScanlation", "Resolviendo manga por lista local/cacheada para '$query'")
        ensureSeriesListLoaded(cacheManager)
        val normalized = query.lowercase()
        val series = cacheManager.getCachedSeriesList()
        return currentId?.let { id -> series?.firstOrNull { it.id?.toString() == id } }
            ?: series?.firstOrNull { it.name.lowercase() == normalized }
            ?: cacheManager.getCachedSeriesList()
                ?.firstOrNull { it.name.lowercase().contains(normalized) }
            ?: cacheManager.getCachedSeriesList()
                ?.firstOrNull { normalized.contains(it.name.lowercase()) }
    }

    fun resolveMangaById(
        id: String,
        cacheManager: MangaCacheManager,
    ): MangaDto? {
        ensureSeriesListLoaded(cacheManager)
        return cacheManager.getCachedSeriesList()?.firstOrNull { it.id?.toString() == id }
    }

    fun resolveMangaBySlug(
        slug: String,
        cacheManager: MangaCacheManager,
    ): MangaDto? {
        ensureSeriesListLoaded(cacheManager)
        val cleanSlug = slug.trim().removeSuffix("/")
        return cacheManager.getCachedSeriesList()?.firstOrNull { it.slug.trim().removeSuffix("/") == cleanSlug }
    }

    /**
     * Fuerza la recarga de la lista completa de series desde la API.
     * Se usa cuando un slug devuelve 404/500 y se necesita el slug actualizado.
     */
    fun forceRefreshSeriesList(cacheManager: MangaCacheManager) {
        synchronized(this) {
            cacheManager.clearCachedSeriesList() // invalidar caché
            loadSeriesList(cacheManager)
        }
    }

    private fun ensureSeriesListLoaded(cacheManager: MangaCacheManager) {
        synchronized(this) {
            if (cacheManager.getCachedSeriesList() != null) return
            loadSeriesList(cacheManager)
        }
    }

    private fun loadSeriesList(cacheManager: MangaCacheManager) {
        val apiUrl = "$websiteBaseUrl/api/series/list"
        try {
            val response =
                client
                    .newCall(
                        Request
                            .Builder()
                            .url(apiUrl)
                            .headers(
                                okhttp3.Headers
                                    .Builder()
                                    .apply { headers.forEach { (k, v) -> add(k, v) } }
                                    .build(),
                            ).build(),
                    ).execute()
            val body = response.body.string()
            if (response.code != 401 && !isErrorPage(response.code, body)) {
                val series = json.decodeMangaListPayload(body)
                cacheManager.setCachedSeriesList(series)
                Log.d("OlympusScanlation", "Lista completa cargada: ${series.size} series")
            } else {
                Log.e("OlympusScanlation", "Error al cargar lista completa: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("OlympusScanlation", "Excepción al cargar lista completa: ${e.message}")
        }
    }

    fun resolveIdBySlug(
        slug: String,
        cacheManager: MangaCacheManager,
    ): String? = resolveMangaBySlug(slug, cacheManager)?.id?.toString()

    fun resolveSlugById(
        id: String,
        title: String?,
        cacheManager: MangaCacheManager,
    ): String? {
        val cached = cacheManager.getCachedSlugForId(id)
        if (!cached.isNullOrBlank()) {
            ensureSeriesListLoaded(cacheManager)
            val match = cacheManager.getCachedSeriesList()?.firstOrNull { it.id?.toString() == id }
            if (match != null && match.slug != cached) {
                cacheManager.updateMangaCache(match)
                return match.slug
            }
            return cached
        }

        val match =
            resolveMangaById(id, cacheManager)
                ?: title?.let { resolveMangaByName(it, id, cacheManager) }
                ?: return null

        cacheManager.updateMangaCache(match)
        return match.slug
    }

    fun resolveSlugForManga(
        manga: SManga,
        cacheManager: MangaCacheManager,
    ): String? {
        var id = UrlUtils.mangaIdFromUrl(manga.url)

        if (id == null) {
            id = cacheManager.resolveIdByTitleFromList(manga.title)
        }

        val cachedSlug = id?.let { cacheManager.getCachedSlugForId(it) }
        if (!cachedSlug.isNullOrBlank()) {
            ensureSeriesListLoaded(cacheManager)
            val match = cacheManager.getCachedSeriesList()?.firstOrNull { it.id?.toString() == id }
            if (match != null && match.slug != cachedSlug) {
                cacheManager.updateMangaCache(match)
                return match.slug
            }
            return cachedSlug
        }

        if (id != null) {
            val matchById = resolveMangaById(id, cacheManager)
            if (matchById != null) {
                cacheManager.updateMangaCache(matchById)
                return matchById.slug
            }
        }

        val match = resolveMangaByName(manga.title, id, cacheManager)
        if (match != null) {
            cacheManager.updateMangaCache(match)
            return match.slug
        }
        return UrlUtils.mangaSlugFromUrl(manga.url)
    }

    fun isErrorPage(
        responseCode: Int,
        body: String,
    ): Boolean {
        if (responseCode == 404 || responseCode == 500) return true
        if (body.contains("Something went wrong", ignoreCase = true)) return true
        return body.contains(">500<")
    }
}
