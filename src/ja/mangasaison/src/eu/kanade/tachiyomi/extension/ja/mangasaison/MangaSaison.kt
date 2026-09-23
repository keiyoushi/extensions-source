package eu.kanade.tachiyomi.extension.ja.mangasaison

import android.util.Base64
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
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.decodeHex
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.zip.Entry
import keiyoushi.zip.coroutines.readZipEntry
import keiyoushi.zip.coroutines.zipDirectory
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.buffer
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class MangaSaison :
    KeiSource(),
    ConfigurableSource {
    private val pageLimit = 30
    private val apiUrl get() = "$baseUrl/api/query"
    private val viewerUrl get() = "$baseUrl/api/v1/mdviewer"
    private val algoliaUrl get() = "https://$ALGOLIA_APP_ID-dsn.algolia.net/1/indexes/cominavi/query"
    private val preferences by getPreferencesLazy()
    private val algoliaHeaders get() = headersBuilder()
        .set("X-Algolia-Application-Id", ALGOLIA_APP_ID)
        .set("X-Algolia-Api-Key", "dfe863b14fd0035402b32fa3bc00d27c")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(GraphQLErrorInterceptor())
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 500 && chain.request().url.pathSegments.getOrNull(2) == "mdviewer") {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val limit = page * pageLimit
        val result = client.post(
            apiUrl,
            graphQLBody(
                query = POPULAR_QUERY,
                operationName = "storeLatestWeeklySalesRankings",
                variables = PopularVariables(listOf("overall"), limit),
            ),
        ).parseGraphQLAs<RankingResponse>().storeLatestWeeklySalesRankings.flatMap(StoreLatestWeeklySalesRanking::ranking)

        val mangas = result.drop((page - 1) * pageLimit).map(Ranking::toSManga)
        val hasNextPage = result.size == limit
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.post(
            apiUrl,
            graphQLBody(
                query = LATEST_QUERY,
                operationName = "newArrivalContents",
                variables = LatestVariables("general", pageLimit, (page - 1) * pageLimit),
            ),
        ).parseGraphQLAs<LatestResponse>().newArrivalContents
        val mangas = result.map(Hit::toSManga)
        val hasNextPage = result.size >= pageLimit
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = SearchRequestBody(
            query = query,
            page = page - 1,
            hitsPerPage = 36,
            filters = "isSearchable=1 AND isValid=1 AND payTitleId=-1",
            attributesToRetrieve = listOf("titleId", "titleName", "compressedTitleThumbnailPath"),
            attributesToHighlight = emptyList(),
            restrictSearchableAttributes = listOf(
                "titleName",
                "titleNameHira",
                "titleNameKana",
                "authors.authorName",
                "authors.authorNameHira",
                "authors.authorNameKana",
                "publisherName",
                "longDescription",
            ),
        ).toJsonRequestBody()
        val result = client.post(algoliaUrl, algoliaHeaders, body).parseAs<SearchResponse>()
        val mangas = result.hits.map(Hit::toSManga)
        val hasNextPage = result.hasNextPage()
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/titles/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val result = client.post(
            apiUrl,
            graphQLBody(
                query = DETAILS_QUERY,
                operationName = "bookTitleDetail",
                variables = DetailsVariables(manga.url.toInt(), 0, "desc"),
            ),
        ).parseGraphQLAs<DetailsResponse>()

        return SMangaUpdate(
            result.bookTitle.toSManga(),
            result.bookContents
                .filter { !hideLocked || (!it.isLocked && !it.isPreview) }
                .map(BookContent::toSChapter),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/mdviewer/browser/${chapter.memo["distributionId"]?.stringOrNull ?: chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val distributionId = chapter.memo["distributionId"]?.stringOrNull ?: chapter.url
        val contentUrl = "$viewerUrl/content".toHttpUrl().newBuilder()
            .addQueryParameter("distributionId", distributionId)
            .build()

        val content = client.get(contentUrl, cacheControl = FORCE_NETWORK).parseAs<ViewerResponse>()
        val accessUrl = "$viewerUrl/access-provider".toHttpUrl().newBuilder()
            .apply { if (content.contentType != "main") addPathSegment(content.contentType) }
            .addQueryParameter("contentId", content.contentId)
            .addQueryParameter("distributionId", distributionId)
            .build()

        val access = client.get(accessUrl, cacheControl = FORCE_NETWORK).parseAs<AccessResponse>()
        val zipUrl = access.url
        val entries = client.zipDirectory(zipUrl).entries.associateBy(Entry::name)
        val spine = client.readZipEntry(zipUrl, entries.getValue("META-INF/package.json")).buffer().parseAs<MdPackage>().spine
        val key = resolveImageKey(access.token)

        return spine.mapIndexed { index, item ->
            val entry = entries.getValue(item.href)
            val data = ImageRequestData(entry.localHeaderOffset, entry.compressedSize, entry.method, key)
            Page(index, imageUrl = "$zipUrl#${data.toJsonString()}")
        }
    }

    // LSUZR::new key schedule (wasm func 70)
    private fun resolveImageKey(token: String): String {
        val parts = token.split('.') // mdc2.<material>.<origin>.<contentId>.<signature>
        // 56 bytes: timestamp header, then 3 wrapped AES blocks
        val material = Base64.decode(parts[1], Base64.URL_SAFE)

        // masterKey = SHA-256(timestamp header || origin || contentId || CHUNK4)
        val master = MessageDigest.getInstance("SHA-256").run {
            update(material, 0, TIMESTAMP_HEADER_SIZE)
            update(Base64.decode(parts[2], Base64.URL_SAFE))
            update(Base64.decode(parts[3], Base64.URL_SAFE))
            digest(CHUNK4)
        }

        // unwrap with the master key (wasm func 89)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(master, "AES"), IvParameterSpec(ByteArray(cipher.blockSize)))
        val unwrapped = cipher.doFinal(material, TIMESTAMP_HEADER_SIZE, material.size - TIMESTAMP_HEADER_SIZE)
        return ByteArray(AES_KEY_SIZE) { unwrapped[AES_KEY_SIZE + it].toInt().inv().toByte() }.toHexString()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val ALGOLIA_APP_ID = "L66VA7452H"

        // static_secret XOR local9 (wasm 1244596 ^ 1244660); the constant 4th SHA-256 block
        private val CHUNK4 = "98b8937ff9fe8aa877d0a0687b90b129940b5e8fbfebd730d62559b1f166fc76".decodeHex()
        private const val TIMESTAMP_HEADER_SIZE = 8
        private const val AES_KEY_SIZE = 16
    }
}
