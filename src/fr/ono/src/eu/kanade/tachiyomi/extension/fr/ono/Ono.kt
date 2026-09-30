package eu.kanade.tachiyomi.extension.fr.ono

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.runWebViewBlocking
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale

@Source
abstract class Ono :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authInterceptor)
        .addInterceptor(::wafInterceptor)
        .addInterceptor(::imageRetryInterceptor)

    private val apiUrl = "https://ws.ono.live/graphql"

    // Cognito stores its JWTs as cookies on the www domain. The website sends the
    // idToken as `Authorization: bearer <jwt>` to the GraphQL API. Mirror that:
    // pull the idToken cookie (set after WebView login) and attach it to API calls.
    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != API_HOST || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val token = bearerToken()
        val newRequest = if (token != null) {
            request.newBuilder().header("Authorization", "bearer $token").build()
        } else {
            request
        }
        return chain.proceed(newRequest)
    }

    private fun bearerToken(): String? {
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        val prefix = "CognitoIdentityServiceProvider.$COGNITO_CLIENT_ID"
        val sub = cookies.firstOrNull { it.name == "$prefix.LastAuthUser" }?.value
            ?: return null
        val token = cookies.firstOrNull { it.name == "$prefix.$sub.idToken" }?.value
            ?: return null
        return token.takeUnless { isJwtExpired(it) }
    }

    private fun isJwtExpired(jwt: String): Boolean = try {
        val payload = jwt.split('.')[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
        val exp = Regex("\"exp\":(\\d+)").find(json)!!.groupValues[1].toLong()
        // Treat as expired slightly early to avoid mid-request expiry.
        System.currentTimeMillis() / 1000 >= exp - 30
    } catch (_: Exception) {
        false
    }

    private fun wafInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sentToken = wafToken()
        val response = chain.proceed(request)
        if (!response.isWafChallenge()) return response
        response.close()

        // The aws-waf-token is only valid for a few minutes; the site's WAF SDK refreshes it,
        // so load the site in a WebView to get a fresh one and retry once.
        synchronized(this) {
            if (wafToken() == sentToken) {
                runCatching {
                    runWebViewBlocking<Unit>(chain.call()) {
                        headers["User-Agent"]?.let { userAgent = it }
                        poll { if (wafToken().let { it != null && it != sentToken }) resolve(Unit) }
                        loadUrl(baseUrl)
                    }
                }
            }
        }

        val retried = chain.proceed(request)
        if (retried.isWafChallenge()) {
            retried.close()
            throw IOException(
                "AWS WAF challenge déclenché. Ouvrez le site dans la WebView " +
                    "pour résoudre le défi de sécurité, puis réessayez.",
            )
        }
        return retried
    }

    private fun Response.isWafChallenge() = code == 202 && header("x-amzn-waf-action") == "challenge"

    private fun wafToken(): String? = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        .firstOrNull { it.name == "aws-waf-token" }?.value

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val rscHeaders: Headers get() = headers.newBuilder().add("RSC", "1").build()

    private val gqlHeaders: Headers
        get() = headers.newBuilder()
            .add("age-confirmed", "true")
            .add("ono-platform", "website")
            .add("ono-product", "FR")
            .build()

    private fun contentPath(contentType: String): String = if (contentType.equals("MANGA", ignoreCase = true)) "manga" else "webtoon"

    // =============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val body = graphQLBody(query = RANKING_QUERY, operationName = "getCatalogRanking")
        val series = client.post(apiUrl, gqlHeaders, body)
            .parseGraphQLAs<RankingData>()
            .getCatalogRanking?.series!!
        val mangas = series.map { s ->
            SManga.create().apply {
                title = s.title
                thumbnail_url = s.imageURL
                setUrlWithoutDomain("/${contentPath(s.contentType)}/${s.slug}")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = graphQLBody(
            query = SEARCH_QUERY,
            operationName = "searchCatalogByTerm",
            variables = SearchVariables(query),
        )
        val series = client.post(apiUrl, gqlHeaders, body)
            .parseGraphQLAs<SearchCatalogData>()
            .searchCatalogByTerm?.series!!
        val mangas = series.map { s ->
            SManga.create().apply {
                title = s.title
                thumbnail_url = "https://catalog.ono.live/master/contents/${s.id}/thumbnail"
                setUrlWithoutDomain("/${contentPath(s.contentType)}/${s.slug}")
            }
        }
        return MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================

    private suspend fun seriesDetail(manga: SManga): SeriesDetail {
        val url = (baseUrl + manga.url).toHttpUrl()
        client.get(url, rscHeaders).extractNextJs<SeriesDetail>()?.let { return it }

        // RSC payload can be partial on client-side navigation; retry cache-busted.
        val retryUrl = url.newBuilder()
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        return client.get(retryUrl, rscHeaders, CacheControl.FORCE_NETWORK).extractNextJs<SeriesDetail>()!!
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = seriesDetail(manga)
        val path = contentPath(series.contentType)

        val updatedManga = SManga.create().apply {
            title = series.title
            thumbnail_url = series.cover
            author = series.contributors.joinToString { it.name }.ifBlank { null }
            artist = author
            description = listOfNotNull(
                series.punchline?.trim()?.takeIf { it.isNotEmpty() },
                series.summary?.trim()?.takeIf { it.isNotEmpty() },
            ).joinToString("\n\n").ifBlank { null }
            genre = (series.genres.map { it.label } + series.tags.map { it.label })
                .mapNotNull { it.trim().takeIf { g -> g.isNotEmpty() } }
                .joinToString { it.replaceFirstChar { c -> c.titlecase(Locale.FRENCH) } }
            status = parseStatus(series.publicationStatus)
            setUrlWithoutDomain("/$path/${series.slug}")
        }

        val showPremium = preferences.getBoolean(SHOW_PREMIUM_KEY, SHOW_PREMIUM_DEFAULT)
        val showWaf = preferences.getBoolean(SHOW_WAF_KEY, SHOW_WAF_DEFAULT)

        val chapterList = series.seriesElements
            .mapNotNull { el ->
                val locked = el.price != null && el.price != "0" && el.isBought != true
                // Any non-null waitAndRead (WaitAndReadAvailable / InUse / ...) = wait-until-free eligible.
                val isWaf = locked && el.waitAndRead != null
                val isPremium = locked && !isWaf

                if (isWaf && !showWaf) return@mapNotNull null
                if (isPremium && !showPremium) return@mapNotNull null

                SChapter.create().apply {
                    val label = el.title?.trim()?.takeIf { it.isNotBlank() } ?: "Episode ${el.num}"
                    name = when {
                        isWaf -> "🕐 $label"
                        isPremium -> "🔒 $label"
                        else -> label
                    }
                    chapter_number = el.num.toFloatOrNull() ?: -1f
                    setUrlWithoutDomain("/$path/${series.slug}/${el.num}")
                }
            }
            .sortedByDescending { it.chapter_number }

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.uppercase()) {
        "ONGOING" -> SManga.ONGOING
        "FINISHED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "UNPUBLISHED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.trim('/').split('/')
        val num = segments.last()
        val slug = segments[segments.size - 2]

        val payload = fetchReadingSession(num, slug) { client.post(apiUrl, gqlHeaders, it) }

        when (payload.__typename) {
            "SessionStarted" -> {}
            "PublicationUnavailable" ->
                throw Exception("Chapitre indisponible: ${payload.unavailabilityReason ?: "inconnu"}")
            "UserHasNotAccess" -> {
                val methods = payload.publicationAccessMethods
                if (methods.any { it.__typename == "NotLoggedIn" }) {
                    throw Exception(
                        "Connexion requise. Connectez-vous au site via la WebView " +
                            "pour lire les chapitres 🕐/🔒.",
                    )
                }
                val used = methods
                    .firstOrNull { it.__typename == "WaitNReadIsUsed" }
                if (used?.waitAndReadReloadDelay != null) {
                    val h = used.waitAndReadReloadDelay / 3600
                    val m = (used.waitAndReadReloadDelay % 3600) / 60
                    throw Exception(
                        "Créneau 'wait until free' épuisé. Prochain déblocage gratuit dans " +
                            "~${h}h${m.toString().padStart(2, '0')}.",
                    )
                }
                throw Exception(
                    "Chapitre premium (coins/ticket requis). Débloquez-le sur le site via la WebView.",
                )
            }
            else -> throw Exception("Accès refusé (${payload.__typename})")
        }

        val pages = payload.publicationMetadata?.pages!!
        val fragment = "$slug/$num"
        return pages.mapIndexed { i, url -> Page(i, imageUrl = "$url#$fragment") }
    }

    // Inline so the same logic runs with suspend calls from getPageList and blocking calls
    // from imageRetryInterceptor.
    private inline fun fetchReadingSession(
        num: String,
        slug: String,
        execute: (RequestBody) -> Response,
    ): ReadingSessionPayload {
        val startReadingBody = graphQLBody(
            query = START_READING_QUERY,
            operationName = "StartReadingSession",
            variables = StartReadingVariables(num, slug),
        )

        var payload = execute(startReadingBody)
            .parseGraphQLAs<StartReadingSessionData>()
            .startReadingSessionBySlugAndNum!!

        if (payload.__typename == "UserHasNotAccess") {
            val wnr = payload.publicationAccessMethods
                .firstOrNull { it.__typename == "WaitNReadAvailable" && it.publicationId != null }
            if (wnr?.publicationId != null) {
                val unlockBody = graphQLBody(
                    query = UNLOCK_WNR_MUTATION,
                    operationName = "unlockPublicationByWnR",
                    variables = UnlockVariables(wnr.publicationId),
                )
                val result = execute(unlockBody)
                    .parseGraphQLAs<UnlockData>()
                    .unlockPublicationByWnR!!
                if (result.success != true) {
                    throw Exception(
                        "Échec du déblocage 'wait until free'" +
                            (result.code?.let { " ($it)" } ?: "") + ".",
                    )
                }

                payload = execute(startReadingBody)
                    .parseGraphQLAs<StartReadingSessionData>()
                    .startReadingSessionBySlugAndNum!!
            }
        }

        return payload
    }

    // CloudFront signed URLs expire. If a chapter is preloaded and read later,
    // images return 403. Re-fetch the reading session to get fresh signed URLs.
    // Chapter slug/num is embedded as a URL fragment (never sent to server).
    private fun imageRetryInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HttpURLConnection.HTTP_FORBIDDEN) return response

        val fragment = chain.request().url.fragment ?: return response
        val parts = fragment.split('/')
        if (parts.size != 2) return response
        val (slug, num) = parts

        response.close()

        val payload = fetchReadingSession(num, slug) {
            client.newCall(POST(apiUrl, gqlHeaders, it)).execute()
        }
        val freshPages = payload.publicationMetadata?.pages
            ?: throw IOException("Pas de pages dans la session rafraîchie")

        val originalPath = chain.request().url.encodedPath
        val freshUrl = freshPages.firstOrNull { it.toHttpUrl().encodedPath == originalPath }
            ?: throw IOException("Page introuvable après rafraîchissement")

        return chain.proceed(
            chain.request().newBuilder()
                .url(freshUrl)
                .build(),
        )
    }

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = SHOW_PREMIUM_KEY
            title = "Afficher les chapitres premium"
            summary = "Afficher les chapitres payants (identifiés par 🔒) dans la liste."
            setDefaultValue(SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)

        CheckBoxPreference(screen.context).apply {
            key = SHOW_WAF_KEY
            title = "Afficher les chapitres 'Wait until free'"
            summary = "Afficher les chapitres lisibles via un créneau d'attente (identifiés par 🕐)."
            setDefaultValue(SHOW_WAF_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val API_HOST = "ws.ono.live"
        private const val COGNITO_CLIENT_ID = "12kanvg0bocd5hjtuul46phv7s"

        private val SEARCH_QUERY =
            $$"query searchCatalogByTerm($term:String!)" +
                $$"{searchCatalogByTerm(input:{term:$term})" +
                "{series{id title contentType slug}}}"

        private val RANKING_QUERY =
            $$"query getCatalogRanking($genreSlug:String)" +
                $$"{getCatalogRanking(filter:{genreSlug:$genreSlug})" +
                "{__typename ...on GetCatalogRankingPayload{" +
                "series{id slug title contentType imageURL}}" +
                "...on ErrorWithCode{__typename code}}}"
        private const val SHOW_PREMIUM_KEY = "show_premium_chapters"
        private const val SHOW_PREMIUM_DEFAULT = false
        private const val SHOW_WAF_KEY = "show_wait_until_free_chapters"
        private const val SHOW_WAF_DEFAULT = true

        private val START_READING_QUERY =
            $$"query StartReadingSession($num:String!$slug:String!)" +
                $$"{startReadingSessionBySlugAndNum(input:{num:$num slug:$slug})" +
                "{...C}}" +
                "fragment C on StartReadingSessionPayload{" +
                "...on PublicationUnavailable{__typename unavailabilityReason}" +
                "...on UserHasNotAccess{__typename publicationAccessMethods{...A}}" +
                "...on ErrorWithCode{__typename code}" +
                "...on SessionStarted{__typename publicationMetadata{pages}}}" +
                "fragment A on PublicationAccessMethod{__typename " +
                "...on WaitNReadIsUsed{waitAndReadReloadDelay}" +
                "...on WaitNReadAvailable{publicationId}" +
                "...on CanBeBought{publicationId}" +
                "...on NotEnoughCoins{publicationId}" +
                "...on GiftTicketsAvailable{publicationId}}"

        private val UNLOCK_WNR_MUTATION =
            $$"mutation unlockPublicationByWnR($publicationId:UUID!)" +
                $$"{unlockPublicationByWnR(input:{publicationId:$publicationId})" +
                "{...on UnlockPublicationResult{success}" +
                "...on ErrorWithCode{__typename code}}}"
    }
}
