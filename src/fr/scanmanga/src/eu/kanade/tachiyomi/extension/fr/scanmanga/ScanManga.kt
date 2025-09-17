package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater

class ScanManga : HttpSource(), ConfigurableSource {
    override val name = "Scan-Manga"

    override val baseUrl = "https://m.scan-manga.com"
    private val baseImageUrl = "https://static.scan-manga.com/img/manga"

    override val lang = "fr"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<android.app.Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")

    // Configuration des préférences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val cacheModePref = ListPreference(screen.context).apply {
            key = "pref_cache_mode"
            title = "Mode de cache des covers"
            summary = "Cache hybride recommandé pour de meilleures performances"
            entries = arrayOf("Cache hybride (recommandé)", "Mémoire uniquement", "Persistant uniquement")
            entryValues = arrayOf("hybrid", "memory", "persistent")
            setDefaultValue("hybrid")
        }
        screen.addPreference(cacheModePref)

        val loadingModePref = ListPreference(screen.context).apply {
            key = "pref_loading_mode"
            title = "Mode de chargement des covers"
            summary = "Rapide = affichage immédiat, Complet = attendre toutes les covers"
            entries = arrayOf("Rapide (affichage immédiat)", "Complet (attendre toutes les covers)")
            entryValues = arrayOf("fast", "complete")
            setDefaultValue("fast")
        }
        screen.addPreference(loadingModePref)

        val batchSizePref = ListPreference(screen.context).apply {
            key = "pref_batch_size"
            title = "Taille des lots de chargement"
            summary = "Plus grand = plus rapide mais plus de charge serveur"
            entries = arrayOf("5 covers", "10 covers", "15 covers", "20 covers")
            entryValues = arrayOf("5", "10", "15", "20")
            setDefaultValue("15")
        }
        screen.addPreference(batchSizePref)
    }

    private fun getCacheMode(): String = preferences.getString("pref_cache_mode", "hybrid") ?: "hybrid"
    private fun getBatchSize(): Int = preferences.getString("pref_batch_size", "15")?.toIntOrNull() ?: 15
    private fun getLoadingMode(): String = preferences.getString("pref_loading_mode", "fast") ?: "fast"

    // Cache mémoire - utilisation de ConcurrentHashMap pour thread safety
    companion object {
        private val memoryCache = ConcurrentHashMap<String, Pair<String, Long>>()
        private const val MEMORY_CACHE_DURATION = 3 * 24 * 60 * 60 * 1000L // 3 jours
    }

    // Cache persistant via SharedPreferences (plus fiable)
    private val persistentCache: ConcurrentHashMap<String, Pair<String, Long>> by lazy {
        ConcurrentHashMap(loadPersistentCache())
    }

    private val persistentCacheDuration = 7 * 24 * 60 * 60 * 1000L // 1 semaine

    private fun loadPersistentCache(): Map<String, Pair<String, Long>> {
        return try {
            val cache = mutableMapOf<String, Pair<String, Long>>()
            val now = System.currentTimeMillis()

            // Charger depuis SharedPreferences
            val allPrefs = preferences.all
            allPrefs.keys.filter { it.startsWith("cover_") }.forEach { key ->
                try {
                    val value = preferences.getString(key, null)
                    if (value != null) {
                        val parts = value.split("|")
                        if (parts.size == 2) {
                            val url = parts[0]
                            val timestamp = parts[1].toLong()

                            // Ne charger que les entrées non expirées
                            if (now - timestamp < persistentCacheDuration) {
                                val mangaUrl = key.removePrefix("cover_")
                                cache[mangaUrl] = url to timestamp
                            } else {
                                // Supprimer l'entrée expirée
                                preferences.edit().remove(key).apply()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Supprimer l'entrée corrompue
                    preferences.edit().remove(key).apply()
                }
            }
            cache
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun savePersistentCache() {
        try {
            val editor = preferences.edit()
            val now = System.currentTimeMillis()

            // Sauvegarder les entrées valides
            persistentCache.entries.forEach { (key, value) ->
                if (now - value.second < persistentCacheDuration) {
                    val prefKey = "cover_$key"
                    val prefValue = "${value.first}|${value.second}"
                    editor.putString(prefKey, prefValue)
                }
            }

            editor.apply()
        } catch (e: Exception) {
            // Ignorer les erreurs silencieusement
        }
    }

    private fun getCoverUrl(mangaUrl: String): String? {
        val now = System.currentTimeMillis()

        return when (getCacheMode()) {
            "hybrid" -> getCoverHybridCache(mangaUrl, now)
            "memory" -> getCoverFromMemoryCache(mangaUrl, now)
            "persistent" -> getCoverFromPersistentCache(mangaUrl, now)
            else -> fetchCoverFromServer(mangaUrl)
        }
    }

    private fun getCoverHybridCache(mangaUrl: String, now: Long): String? {
        // Niveau 1 : vérifier cache mémoire d'abord (le plus rapide)
        memoryCache[mangaUrl]?.let { cached ->
            if (now - cached.second < MEMORY_CACHE_DURATION) {
                return cached.first
            } else {
                // Retirer l'entrée expirée
                memoryCache.remove(mangaUrl)
            }
        }

        // Niveau 2 : vérifier cache persistant
        persistentCache[mangaUrl]?.let { cached ->
            if (now - cached.second < persistentCacheDuration) {
                // Remonter dans le cache mémoire pour accès futurs plus rapides
                memoryCache[mangaUrl] = cached
                return cached.first
            } else {
                // Retirer l'entrée expirée
                persistentCache.remove(mangaUrl)
            }
        }

        // Niveau 3 : récupérer du serveur et mettre en cache aux deux niveaux
        return fetchCoverFromServer(mangaUrl)?.also { coverUrl ->
            val entry = coverUrl to now
            memoryCache[mangaUrl] = entry
            persistentCache[mangaUrl] = entry
            // Sauvegarder en arrière-plan pour ne pas bloquer
            Thread {
                savePersistentCache()
            }.start()
        }
    }

    private fun getCoverFromMemoryCache(mangaUrl: String, now: Long): String? {
        memoryCache[mangaUrl]?.let { cached ->
            if (now - cached.second < MEMORY_CACHE_DURATION) {
                return cached.first
            } else {
                memoryCache.remove(mangaUrl)
            }
        }

        // Pas en cache valide, récupérer du serveur
        return fetchCoverFromServer(mangaUrl)?.also { coverUrl ->
            memoryCache[mangaUrl] = coverUrl to now
        }
    }

    private fun getCoverFromPersistentCache(mangaUrl: String, now: Long): String? {
        persistentCache[mangaUrl]?.let { cached ->
            if (now - cached.second < persistentCacheDuration) {
                return cached.first
            } else {
                persistentCache.remove(mangaUrl)
            }
        }

        // Pas en cache valide, récupérer du serveur
        return fetchCoverFromServer(mangaUrl)?.also { coverUrl ->
            persistentCache[mangaUrl] = coverUrl to now
            Thread {
                savePersistentCache()
            }.start()
        }
    }

    private fun fetchCoverFromServer(mangaUrl: String): String? {
        return try {
            val doc = client.newCall(GET(baseUrl + mangaUrl, headers)).execute().use {
                it.asJsoup()
            }
            val coverUrl = doc.select("div.full_img_serie img[itemprop=image]").attr("src")
            if (coverUrl.isNotBlank()) coverUrl else null
        } catch (e: Exception) {
            null
        }
    }

    // Chargement optimisé en bloc - ultra rapide pour les cache hits
    private fun loadCoversInBulk(mangas: List<SManga>) {
        val now = System.currentTimeMillis()
        val mangasNeedingCovers = mutableListOf<SManga>()

        // Phase 1 : Récupération ultra-rapide depuis les caches existants
        mangas.forEach { manga ->
            val coverUrl = when (getCacheMode()) {
                "hybrid" -> {
                    // Cache mémoire d'abord (instantané)
                    memoryCache[manga.url]?.let { cached ->
                        if (now - cached.second < MEMORY_CACHE_DURATION) {
                            cached.first
                        } else {
                            memoryCache.remove(manga.url)
                            null
                        }
                    }
                    // Cache persistant ensuite (rapide)
                        ?: persistentCache[manga.url]?.let { cached ->
                            if (now - cached.second < persistentCacheDuration) {
                                // Remonter automatiquement en cache mémoire
                                memoryCache[manga.url] = cached
                                cached.first
                            } else {
                                persistentCache.remove(manga.url)
                                null
                            }
                        }
                }
                "memory" -> {
                    memoryCache[manga.url]?.let { cached ->
                        if (now - cached.second < MEMORY_CACHE_DURATION) {
                            cached.first
                        } else {
                            memoryCache.remove(manga.url)
                            null
                        }
                    }
                }
                "persistent" -> {
                    persistentCache[manga.url]?.let { cached ->
                        if (now - cached.second < persistentCacheDuration) {
                            cached.first
                        } else {
                            persistentCache.remove(manga.url)
                            null
                        }
                    }
                }
                else -> null
            }

            if (coverUrl != null) {
                manga.thumbnail_url = coverUrl
            } else {
                mangasNeedingCovers.add(manga)
            }
        }

        // Phase 2 : Chargement des covers manquantes uniquement si nécessaire
        if (mangasNeedingCovers.isNotEmpty()) {
            when (getLoadingMode()) {
                "fast" -> {
                    // Mode rapide : chargement en arrière-plan, affichage immédiat
                    Thread {
                        loadMissingCoversInBackground(mangasNeedingCovers)
                    }.start()
                }
                "complete" -> {
                    // Mode complet : attendre le chargement de toutes les covers
                    loadMissingCoversInBackground(mangasNeedingCovers)
                }
            }
        }
    }

    private fun loadMissingCoversInBackground(mangas: List<SManga>) {
        val batchSize = getBatchSize()
        mangas.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            // Traitement parallèle du batch courant (compatible API 21+)
            val threads = batch.map { manga ->
                Thread {
                    fetchCoverFromServer(manga.url)?.let { coverUrl ->
                        val now = System.currentTimeMillis()
                        val entry = coverUrl to now
                        manga.thumbnail_url = coverUrl
                        memoryCache[manga.url] = entry
                        persistentCache[manga.url] = entry
                    }
                }
            }

            // Lancer tous les threads du batch
            threads.forEach { it.start() }
            // Attendre la fin du batch
            threads.forEach {
                try { it.join() } catch (e: InterruptedException) { return }
            }

            // Délai entre batches pour éviter la surcharge serveur
            if (batchIndex < mangas.chunked(batchSize).size - 1) {
                Thread.sleep(200L)
            }
        }

        // Sauvegarder le cache une seule fois à la fin
        Thread { savePersistentCache() }.start()
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/TOP-Manga-Webtoon-36.html", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("#carouselTOPContainer > div.top").map { element ->
            val titleElement = element.selectFirst("a.atop")!!
            val mangaUrl = titleElement.attr("href")

            SManga.create().apply {
                title = titleElement.text()
                setUrlWithoutDomain(mangaUrl)
                thumbnail_url = null // Sera rempli par le chargement en bloc optimisé
            }
        }

        // Chargement en bloc ultra-rapide si en cache
        loadCoversInBulk(mangas)

        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("#content_news .publi").map { element ->
            val mangaElement = element.selectFirst("a.l_manga")!!
            val mangaUrl = mangaElement.attr("href")

            SManga.create().apply {
                title = mangaElement.text()
                setUrlWithoutDomain(mangaUrl)
                thumbnail_url = null // Sera rempli par le chargement en bloc optimisé
            }
        }

        // Chargement en bloc ultra-rapide si en cache
        loadCoversInBulk(mangas)

        return MangasPage(mangas, false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/search/quick.json"
            .toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .build()
            .toString()

        val newHeaders = headers.newBuilder()
            .add("Content-type", "application/json; charset=UTF-8")
            .build()

        return GET(url, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.body.string()
        if (json == "[]") return MangasPage(emptyList(), false)

        return MangasPage(
            json.parseAs<MangaSearchDto>().title?.map {
                SManga.create().apply {
                    title = it.nom_match
                    setUrlWithoutDomain(it.url)
                    thumbnail_url = "$baseImageUrl/${it.image}" // déjà miniature verticale
                }
            } ?: emptyList(),
            false,
        )
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.select("h1.main_title[itemprop=name]").text()
            author = document.select("div[itemprop=author]").text()
            description = document.selectFirst("div.titres_desc[itemprop=description]")?.text()
            genre = document.selectFirst("div.titres_souspart span[itemprop=genre]")?.text()

            val statutText = document.selectFirst("div.titres_souspart")?.ownText()
            status = when {
                statutText?.contains("En cours", ignoreCase = true) == true -> SManga.ONGOING
                statutText?.contains("Terminé", ignoreCase = true) == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            thumbnail_url = document.select("div.full_img_serie img[itemprop=image]").attr("src")
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapt_m").map { element ->
            val linkEl = element.selectFirst("td.publimg span.i a")!!
            val titleEl = element.selectFirst("td.publititle")

            val chapterName = linkEl.text()
            val extraTitle = titleEl?.text()

            SChapter.create().apply {
                name = if (!extraTitle.isNullOrEmpty()) "$chapterName - $extraTitle" else chapterName
                setUrlWithoutDomain(linkEl.absUrl("href"))
            }
        }
    }

    // Pages
    private fun decodeHunter(obfuscatedJs: String): String {
        val regex = Regex("""eval\(function\(h,u,n,t,e,r\)\{.*?\}\("([^"]+)",\d+,"([^"]+)",(\d+),(\d+),\d+\)\)""")
        val (encoded, mask, intervalStr, optionStr) = regex.find(obfuscatedJs)?.destructured
            ?: error("Failed to match obfuscation pattern: $obfuscatedJs")

        val interval = intervalStr.toInt()
        val option = optionStr.toInt()
        val delimiter = mask[option]
        val tokens = encoded.split(delimiter).filter { it.isNotEmpty() }
        val reversedMap = mask.withIndex().associate { it.value to it.index }

        return buildString {
            for (token in tokens) {
                val digitString = token.map { c ->
                    reversedMap[c]?.toString() ?: error("Invalid masked character: $c")
                }.joinToString("")

                val number = digitString.toIntOrNull(option)
                    ?: error("Failed to parse token: $digitString as base $option")

                val originalCharCode = number - interval

                append(originalCharCode.toChar())
            }
        }
    }

    private fun dataAPI(data: String, idc: Int): UrlPayload {
        val compressedBytes = Base64.decode(data, Base64.NO_WRAP or Base64.NO_PADDING)
        val inflater = Inflater()
        inflater.setInput(compressedBytes)
        val outputBuffer = ByteArray(512 * 1024)
        val decompressedLength = inflater.inflate(outputBuffer)
        inflater.end()

        val inflated = String(outputBuffer, 0, decompressedLength)
        val hexIdc = idc.toString(16)
        val cleaned = inflated.removeSuffix(hexIdc)
        val reversed = cleaned.reversed()

        val finalJsonStr = String(Base64.decode(reversed, Base64.DEFAULT))

        return finalJsonStr.parseAs<UrlPayload>()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val packedScript = document.selectFirst("script:containsData(h,u,n,t,e,r)")!!.data()

        val unpackedScript = decodeHunter(packedScript)
        val parametersRegex = Regex("""sml = '([^']+)';\n?.*var sme = '([^']+)'""")

        val (sml, sme) = parametersRegex.find(unpackedScript)?.destructured
            ?: error("Failed to extract parameters from script.")

        val chapterInfoRegex = Regex("""const idc = (\d+)""")
        val (chapterId) = chapterInfoRegex.find(packedScript)?.destructured
            ?: error("Failed to extract chapter ID.")

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val requestBody = """{"a":"$sme","b":"$sml"}"""

        val documentUrl = document.baseUri().toHttpUrl()

        val pageListRequest = POST(
            "$baseUrl/api/lel/$chapterId.json",
            headers.newBuilder()
                .add("Origin", "${documentUrl.scheme}://${documentUrl.host}")
                .add("Referer", documentUrl.toString())
                .add("Token", "yf")
                .build(),
            requestBody.toRequestBody(mediaType),
        )

        val lelResponse = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
            .newCall(pageListRequest).execute().use { response ->
                if (!response.isSuccessful) { error("Unexpected error while fetching lel.") }
                dataAPI(response.body.string(), chapterId.toInt())
            }

        return lelResponse.generateImageUrls().map { Page(it.first, imageUrl = it.second) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headers.newBuilder()
            .add("Origin", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
