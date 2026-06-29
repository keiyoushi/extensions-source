package eu.kanade.tachiyomi.extension.fr.hanabook

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.EditTextPreference
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
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import kotlin.random.Random

@Source
abstract class HanaBook :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val apiUrl = "https://api.hana-book.fr/api-ebook/v14"
    private val imgCoverUrl = "https://www.boys-loves.fr/yaoi/images/visuels/manga/mobile_600"
    private val blImgUrl = "https://www.boys-loves.fr/api-ebook/bl-img"

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(::authInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // =============================== HTTP =================================

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.url.host != "api.hana-book.fr") {
            return chain.proceed(req)
        }
        val auth = bearerHeader()
        return chain.proceed(req.newBuilder().header("Authorization", auth).build())
    }

    private fun bearerHeader(): String {
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        val raw = cookies.firstOrNull { it.name == "tokenEbook" }?.value
            ?: return "Bearer null"
        val token = raw.replace("%22", "").replace("%3D", "=")
        return "Bearer $token"
    }

    // ============================== Helpers ===============================

    private val padChars = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toCharArray()

    private fun pad(n: Int): String = buildString(n) {
        repeat(n) { append(padChars[Random.nextInt(padChars.size)]) }
    }

    private fun fill(prefix: Int, value: String, suffix: Int): String = pad(prefix) + value + pad(suffix)

    private fun b64(s: String): String = Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    // Identity 10x10 permutation key → server returns unscrambled image.
    private val identityKey: String by lazy {
        val s = buildString {
            for (i in 1..10) {
                for (j in 1..10) {
                    if (isNotEmpty()) append('|')
                    append(i).append(';').append(j)
                }
            }
        }
        b64(s)
    }

    private fun coverUrl(ref: Int?): String? = ref?.let { "$imgCoverUrl/$it.jpg" }

    private fun mangaUrl(idSerie: Int, seo: String, ref: Int): String = "/manga/$seo/$idSerie/$ref"

    private fun refFromUrl(url: String): Int = url.trimEnd('/').substringAfterLast('/').toInt()

    // =============================== Login ================================

    private fun ensureLogin() {
        val email = preferences.getString(EMAIL_KEY, "").orEmpty()
        val password = preferences.getString(PASSWORD_KEY, "").orEmpty()
        if (email.isBlank() || password.isBlank()) return
        if (hasToken()) return
        login(email, password)
    }

    private fun hasToken(): Boolean = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        .any { it.name == "tokenEbook" }

    private fun login(email: String, password: String) {
        val body = LoginRequest(
            ident = email,
            pass = fill(18, b64(password), 9),
        ).toJsonRequestBody()

        val loginHeaders = headersBuilder()
            .set("Authorization", "Bearer null")
            .set("Referer", "$baseUrl/hana-book/login")
            .build()

        val req = POST("$apiUrl/user/", loginHeaders, body)
        val data = client.newCall(req).execute().parseAs<LoginResponse>(transform = ::stripXssi)
        val token = data.token ?: throw Exception("Login échoué: ${data.message ?: "identifiants invalides"}")

        val wrapped = b64(fill(7, token, 4))
        val cookieValue = "%22" + wrapped.replace("=", "%3D") + "%22"

        client.cookieJar.saveFromResponse(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("tokenEbook").value(cookieValue)
                    .domain("hana-book.fr").build(),
            ),
        )
    }

    // =============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = produitsInfoRequest(7)
    override fun popularMangaParse(response: Response): MangasPage = produitsInfoParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = produitsInfoRequest(6)
    override fun latestUpdatesParse(response: Response): MangasPage = produitsInfoParse(response)

    private fun produitsInfoRequest(id: Int): Request = GET("$apiUrl/produits/info/?id=$id&option=0&filterAbo=false&filterPasDejaLu=false", headers)

    private fun produitsInfoParse(response: Response): MangasPage {
        val data = response.parseAs<LatestResponse>(transform = ::stripXssi)
        val seen = HashSet<String>()
        val mangas = data.ebooks?.ebooks.orEmpty().mapNotNull { e ->
            val ref = e.ref.toIntOrNull() ?: return@mapNotNull null
            val base = stripVolumeSuffix(e.titre)
            if (!seen.add(base)) return@mapNotNull null
            SManga.create().apply {
                title = base
                thumbnail_url = coverUrl(ref)
                author = e.auteur
                genre = e.genres.joinToString()
                setUrlWithoutDomain(mangaUrl(0, e.seo, ref))
            }
        }
        return MangasPage(mangas, false)
    }

    private val volumeSuffixRegex = Regex("""\s*[-–]\s*Tome\s+0*\d+.*$""", RegexOption.IGNORE_CASE)

    private fun stripVolumeSuffix(titre: String): String = titre.replace(volumeSuffixRegex, "").trim()

    private fun catalogueParse(response: Response): MangasPage {
        val data = response.parseAs<CatalogueResponse>(transform = ::stripXssi)
        val mangas = data.licences.mapNotNull { l ->
            val ref = l.coverRef ?: return@mapNotNull null
            SManga.create().apply {
                title = l.titreSerie
                thumbnail_url = coverUrl(l.coverRef)
                genre = l.genres.joinToString { it.nom }
                setUrlWithoutDomain(mangaUrl(l.idSerie, l.seo, ref))
            }
        }
        return MangasPage(mangas, data.page < data.nbPages)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .addQueryParameter("filterAbo", "false")
                .build()
            return GET(url, headers)
        }
        return catalogueRequest(page, filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val path = response.request.url.encodedPath
        return if (path.contains("/search/")) {
            val data = response.parseAs<SearchResponse>(transform = ::stripXssi)
            val seen = HashSet<Int>()
            val mangas = data.ebooks
                .filter { seen.add(it.idSerie) }
                .map { e ->
                    SManga.create().apply {
                        title = e.titreSerie
                        thumbnail_url = coverUrl(e.ref)
                        author = e.auteur
                        genre = e.genres.joinToString()
                        setUrlWithoutDomain(mangaUrl(e.idSerie, e.seo, e.ref))
                    }
                }
            MangasPage(mangas, false)
        } else {
            catalogueParse(response)
        }
    }

    // =============================== Catalogue ============================

    private fun catalogueRequest(page: Int, filters: FilterList): Request {
        val builder = "$apiUrl/catalogue/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")

        var filterAbo = false
        val genres = mutableListOf<Int>()
        val collections = mutableListOf<Int>()
        val ages = mutableListOf<Int>()
        val types = mutableListOf<Int>()

        filters.forEach { f ->
            when (f) {
                is AboFilter -> filterAbo = f.state
                is GenresFilter -> f.state.filter { it.state }.forEach { genres += it.id }
                is CollectionsFilter -> f.state.filter { it.state }.forEach { collections += it.id }
                is AgesFilter -> f.state.filter { it.state }.forEach { ages += it.id }
                is TypesFilter -> f.state.filter { it.state }.forEach { types += it.id }
                else -> {}
            }
        }

        builder.addQueryParameter("filterAbo", filterAbo.toString())
        if (genres.isNotEmpty()) builder.addQueryParameter("genres", genres.joinToString(","))
        if (collections.isNotEmpty()) builder.addQueryParameter("collections", collections.joinToString(","))
        if (ages.isNotEmpty()) builder.addQueryParameter("ages", ages.joinToString(","))
        if (types.isNotEmpty()) builder.addQueryParameter("types", types.joinToString(","))

        return GET(builder.build(), headers)
    }

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = getGlobalFilterList(apiUrl, client, headers)

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String {
        val parts = manga.url.trim('/').split('/')
        val seo = parts.getOrNull(1) ?: return baseUrl
        val ref = parts.lastOrNull() ?: return baseUrl
        return "$baseUrl/ebook/${seo.trimEnd('-')}/$ref"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val ref = refFromUrl(manga.url)
        return GET("$apiUrl/produit/?id=$ref", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val ebook = response.parseAs<ProduitResponse>(transform = ::stripXssi).ebook
            ?: throw Exception("Ebook introuvable")

        val idSerie = ebook.idSerie.takeIf { it != 0 }

        // Optional enrichment via licence-dossier (curated wiki-style data).
        val dossier = idSerie?.let { runCatching { fetchDossier(it) }.getOrNull() }

        return SManga.create().apply {
            title = ebook.titreSerie.ifBlank { stripVolumeSuffix(ebook.titre) }
            thumbnail_url = dossier?.cover?.jpg ?: dossier?.cover?.webp ?: coverUrl(ebook.ref)
            author = ebook.auteur
            description = buildDescription(ebook, dossier)
            genre = (listOfNotNull(dossier?.categoryLabel) + ebook.genres).distinct().joinToString()
            status = SManga.UNKNOWN
        }
    }

    private fun fetchDossier(idSerie: Int): Dossier? {
        val req = GET("$apiUrl/licence-dossier/?id_licence=$idSerie", headers)
        return client.newCall(req).execute().use {
            it.parseAs<DossierResponse>(transform = ::stripXssi).dossier
        }
    }

    private fun buildDescription(e: Ebook, d: Dossier?): String {
        val parts = mutableListOf<String>()
        e.description?.takeIf { it.isNotBlank() }?.let { parts += stripHtml(it) }
        if (e.numVolume != null && (e.nbVolumeTotal ?: 0) > 0) {
            parts += "Tome ${e.numVolume}/${e.nbVolumeTotal}"
        }
        listOfNotNull(
            e.collection?.let { "Collection: $it" },
            e.editeurVo?.let { "Éditeur VO: $it" },
            e.dateParutionEbook?.let { "Parution: $it" },
            e.typePublic?.let { "Public: $it" },
        ).takeIf { it.isNotEmpty() }?.let { parts += it.joinToString("\n") }

        d?.dossier?.publication?.let { parts += stripHtml(it) }
        d?.dossier?.auteurs?.let { parts += "Auteur(s):\n" + stripHtml(it) }
        d?.dossier?.adaptations?.let { parts += "Adaptations:\n" + stripHtml(it) }
        d?.dossier?.reception?.let { parts += "Réception:\n" + stripHtml(it) }
        if (d != null && d.characters.isNotEmpty()) {
            parts += "Personnages: " + d.characters.joinToString { c ->
                if (c.role != null) "${c.name} (${c.role})" else c.name
            }
        }
        return parts.joinToString("\n\n").ifBlank { "" }
    }

    private fun stripHtml(html: String): String = html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("</p>"), "\n\n")
        .replace(Regex("<li>"), "• ")
        .replace(Regex("</li>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        ensureLogin()
        val ref = refFromUrl(manga.url)
        return GET("$apiUrl/produit/?id=$ref", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val ebook = response.parseAs<ProduitResponse>(transform = ::stripXssi).ebook ?: return emptyList()

        if (ebook.nbProduitsSerie <= 1) {
            return listOf(buildChapter(ebook.ref, ebook.titre, ebook.numVolume ?: 1, ebook.nbPages))
        }

        val searchUrl = "$apiUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("key", ebook.titreSerie)
            .addQueryParameter("filterAbo", "false")
            .build()
        val volumes = client.newCall(GET(searchUrl, headers)).execute()
            .use { it.parseAs<SearchResponse>(transform = ::stripXssi).ebooks }
            .filter { it.idSerie == ebook.idSerie }

        return volumes
            .sortedByDescending { it.numVolume ?: extractVolumeNum(it.titre) ?: it.ref }
            .map { v ->
                val num = v.numVolume ?: extractVolumeNum(v.titre) ?: 1
                buildChapter(v.ref, v.titre, num, v.nbPages)
            }
    }

    private fun buildChapter(ref: Int, titre: String, num: Int, nbPages: Int): SChapter = SChapter.create().apply {
        name = titre
        chapter_number = num.toFloat()
        setUrlWithoutDomain("/lecteur/$ref?n=$nbPages")
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val ref = chapter.url.trim('/').substringAfter('/').substringBefore('?')
        return "$baseUrl/hana-book/lecteur/$ref"
    }

    private val volumeRegex = Regex("""Tome\s+0*(\d+)""", RegexOption.IGNORE_CASE)

    private fun extractVolumeNum(titre: String): Int? = volumeRegex.find(titre)?.groupValues?.get(1)?.toIntOrNull()

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        ensureLogin()
        val httpUrl = (baseUrl + chapter.url).toHttpUrl()
        val ref = httpUrl.pathSegments.last().toInt()
        val nbPages = httpUrl.queryParameter("n")?.toIntOrNull() ?: 20
        val url = "$apiUrl/images/".toHttpUrl().newBuilder()
            .addQueryParameter("ref", ref.toString())
            .addQueryParameter("p", "1")
            .addQueryParameter("w", "1486")
            .addQueryParameter("h", "842")
            .addQueryParameter("q", "0")
            .addQueryParameter("webp", "true")
            .addQueryParameter("nb_pages", nbPages.toString())
            .addQueryParameter("devicePixelRatio", "1.25")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ImagesResponse>(transform = ::stripXssi)
        if (data.images.isEmpty()) {
            throw Exception(data.message ?: "Aucune image (connexion ou abonnement requis)")
        }
        return data.images.mapIndexed { i, img ->
            val url = "$blImgUrl?p=${img.param}&k=$identityKey"
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = EMAIL_KEY
            title = "Email"
            summary = "Email du compte Hana Book (pour accès aux pages premium)"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                clearToken()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_KEY
            title = "Mot de passe"
            summary = "Mot de passe du compte"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                clearToken()
                true
            }
        }.also(screen::addPreference)
    }

    private fun clearToken() {
        val jar = client.cookieJar
        val current = jar.loadForRequest(baseUrl.toHttpUrl())
        val expired = current.filter { it.name == "tokenEbook" }.map {
            Cookie.Builder()
                .name("tokenEbook").value("")
                .domain("hana-book.fr").expiresAt(0L).build()
        }
        if (expired.isNotEmpty()) jar.saveFromResponse(baseUrl.toHttpUrl(), expired)
    }

    companion object {
        private const val EMAIL_KEY = "hb_email"
        private const val PASSWORD_KEY = "hb_password"
    }
}

/** Strip XSSI prefix `)]}',` Google-style JSON guard. */
internal fun stripXssi(raw: String): String {
    val trimmed = raw.trimStart()
    return if (trimmed.startsWith(")]}'")) {
        trimmed.substringAfter('\n', trimmed.removePrefix(")]}',"))
    } else {
        trimmed
    }
}
