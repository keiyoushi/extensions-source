package eu.kanade.tachiyomi.extension.fr.ono

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.CheckBoxPreference
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
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale

class Ono :
    HttpSource(),
    ConfigurableSource {

    override val name = "Ono"
    override val baseUrl = "https://www.ono.live"
    override val lang = "fr"
    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::authInterceptor)
        .addInterceptor(::wafInterceptor)
        .build()

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
        val response = chain.proceed(chain.request())
        if (response.code == 202 && response.header("x-amzn-waf-action") == "challenge") {
            response.close()
            throw IOException(
                "AWS WAF challenge déclenché. Ouvrez le site dans la WebView " +
                    "pour résoudre le défi de sécurité, puis réessayez.",
            )
        }
        return response
    }

    private val apiUrl = "https://ws.ono.live/graphql"

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val rscHeaders by lazy { headersBuilder().add("RSC", "1").build() }

    private val gqlHeaders by lazy {
        Headers.Builder()
            .add("Content-Type", "application/json")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .add("ono-platform", "website")
            .add("ono-product", "FR")
            .build()
    }

    private fun contentPath(contentType: String): String = if (contentType.equals("MANGA", ignoreCase = true)) "manga" else "webtoon"

    // =============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val payload = buildJsonObject {
            put("operationName", "getCatalogRanking")
            put("query", RANKING_QUERY)
            put("variables", buildJsonObject {})
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return POST(apiUrl, gqlHeaders, payload)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val series = response.parseAs<GraphQLResponse<RankingData>>()
            .data?.getCatalogRanking?.series.orEmpty()
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

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val gqlQuery = $$"query searchCatalogByTerm($term:String!)" +
            $$"{searchCatalogByTerm(input:{term:$term})" +
            "{series{id title contentType slug}}}"
        val payload = buildJsonObject {
            put("operationName", "searchCatalogByTerm")
            put("query", gqlQuery)
            put(
                "variables",
                buildJsonObject { put("term", query) },
            )
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return POST(apiUrl, gqlHeaders, payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val series = response.parseAs<GraphQLResponse<SearchCatalogData>>()
            .data?.searchCatalogByTerm?.series.orEmpty()
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

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    private fun seriesDetail(response: Response): SeriesDetail {
        val body = response.body.string()
        body.extractSeries()?.let { return it }

        // RSC payload can be partial on client-side navigation; retry cache-busted.
        val retryUrl = response.request.url.newBuilder()
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val retry = client.newCall(
            response.request.newBuilder().url(retryUrl)
                .header("Cache-Control", "no-cache").build(),
        ).execute()
        return retry.body.string().extractSeries()
            ?: throw Exception("Impossible de charger la série")
    }

    private fun String.extractSeries(): SeriesDetail? = try {
        this.extractNextJsRsc<SeriesDetail>()
    } catch (_: Exception) {
        null
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = seriesDetail(response)
        return SManga.create().apply {
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
            setUrlWithoutDomain("/${contentPath(series.contentType)}/${series.slug}")
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.uppercase()) {
        "ONGOING" -> SManga.ONGOING
        "FINISHED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "UNPUBLISHED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = seriesDetail(response)
        val path = contentPath(series.contentType)
        val showPremium = preferences.getBoolean(SHOW_PREMIUM_KEY, SHOW_PREMIUM_DEFAULT)
        val showWaf = preferences.getBoolean(SHOW_WAF_KEY, SHOW_WAF_DEFAULT)

        return series.seriesElements
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
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.trim('/').split('/')
        val num = segments.last()
        val slug = segments[segments.size - 2]
        return startReadingRequest(num, slug)
    }

    private fun startReadingRequest(num: String, slug: String): Request {
        val payload = buildJsonObject {
            put("operationName", "StartReadingSession")
            put("query", START_READING_QUERY)
            put(
                "variables",
                buildJsonObject {
                    put("num", num)
                    put("slug", slug)
                },
            )
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return POST(apiUrl, gqlHeaders, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        var payload = response.parseAs<GraphQLResponse<StartReadingSessionData>>()
            .data?.startReadingSessionBySlugAndNum
            ?: throw Exception("Réponse GraphQL invalide")

        // Wait-until-free chapters come back as UserHasNotAccess until unlocked.
        // If a WaitNReadAvailable method is offered, claim it for free, then retry.
        if (payload.__typename == "UserHasNotAccess") {
            val wnr = payload.publicationAccessMethods
                .firstOrNull { it.__typename == "WaitNReadAvailable" && it.publicationId != null }
            if (wnr?.publicationId != null) {
                unlockByWaitAndRead(wnr.publicationId)
                val retry = client.newCall(response.request.newBuilder().build()).execute()
                payload = retry.parseAs<GraphQLResponse<StartReadingSessionData>>()
                    .data?.startReadingSessionBySlugAndNum
                    ?: throw Exception("Réponse GraphQL invalide")
            }
        }

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

        return payload.publicationMetadata?.pages.orEmpty()
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    private fun unlockByWaitAndRead(publicationId: String) {
        val body = buildJsonObject {
            put("operationName", "unlockPublicationByWnR")
            put("query", UNLOCK_WNR_MUTATION)
            put(
                "variables",
                buildJsonObject { put("publicationId", publicationId) },
            )
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val result = client.newCall(POST(apiUrl, gqlHeaders, body)).execute()
            .parseAs<GraphQLResponse<UnlockData>>()
            .data?.unlockPublicationByWnR
        if (result?.success != true) {
            throw Exception(
                "Échec du déblocage 'wait until free'" +
                    (result?.code?.let { " ($it)" } ?: "") + ".",
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

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
