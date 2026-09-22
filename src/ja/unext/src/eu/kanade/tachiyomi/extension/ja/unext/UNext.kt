package eu.kanade.tachiyomi.extension.ja.unext

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.GraphQLException
import keiyoushi.utils.decodeHex
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLGet
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.persistedQueryExtension
import keiyoushi.utils.readIntLittleEndian
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.zip.Entry
import keiyoushi.zip.coroutines.readZipEntry
import keiyoushi.zip.coroutines.zipDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.buffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

@Source
abstract class UNext :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "https://cc.unext.jp"
    private val preferences by getPreferencesLazy()
    private val apiHeaders get() = headersBuilder()
        .set("Content-Type", "application/json")
        .set("Apollographql-Client-Name", "cosmo")
        .build()

    private val keyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    // Paid chapters redirect to the app on mobile UA, but are readable with desktop UA
    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(GraphQLErrorInterceptor())
        addInterceptor(ImageInterceptor())
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.graphQLGet(
            apiUrl,
            apiHeaders,
            operationName = "cosmo_getBookRanking",
            variables = PopularVariables("D_C_COMIC", page, 20),
            extensions = persistedQueryExtension(POPULAR_QUERY_HASH),
            cacheControl = FORCE_NETWORK,
        ).parseGraphQLAs<PopularResponse>().bookRanking
        val mangas = result.books.map { it.bookSakuhin.toSManga() }
        return MangasPage(mangas, result.pageInfo.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.graphQLGet(
            apiUrl,
            apiHeaders,
            operationName = "cosmo_getNewBooks",
            variables = LatestVariables("TAG0000014500", page, 20),
            extensions = persistedQueryExtension(LATEST_QUERY_HASH),
            cacheControl = FORCE_NETWORK,
        ).parseGraphQLAs<LatestResponse>().newBooks
        val mangas = result.books.map { it.toSManga() }
        return MangasPage(mangas, result.pageInfo.hasNextPage())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val result = client.graphQLGet(
            apiUrl,
            apiHeaders,
            operationName = "cosmo_bookFreewordSearch",
            variables = SearchVariables(query, page, 20, null, "RECOMMEND"),
            extensions = persistedQueryExtension(SEARCH_QUERY_HASH),
            cacheControl = FORCE_NETWORK,
        ).parseGraphQLAs<SearchResponse>().search
        val mangas = result.books.map { it.toSManga() }
        return MangasPage(mangas, result.pageInfo.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/book/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val sakuhinCode = manga.url.substringAfter("/book/title/") // for old url compatibility
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)

        val details = async {
            if (!fetchDetails) return@async manga
            client.graphQLGet(
                apiUrl,
                apiHeaders,
                operationName = "cosmo_bookTitleDetail",
                variables = DetailsVariables(sakuhinCode, "TOTAL", 2, 5),
                extensions = persistedQueryExtension(DETAILS_QUERY_HASH),
                cacheControl = FORCE_NETWORK,
            ).parseGraphQLAs<DetailsResponse>().bookTitle.toSManga()
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.graphQLGet(
                apiUrl,
                apiHeaders,
                operationName = "cosmo_bookTitleBooks",
                variables = ChapterListVariables(sakuhinCode, 1, 9999),
                extensions = persistedQueryExtension(CHAPTER_LIST_QUERY_HASH),
                cacheControl = FORCE_NETWORK,
            ).parseGraphQLAs<ChapterListResponse>().bookTitleBooks.books
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter(sakuhinCode) }
                .reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/book/view/${chapter.memo["sakuhinCode"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = coroutineScope {
        val bookFileCode = chapter.memo["bookFileCode"]?.string
            ?: throw Exception("This product is not available yet.")

        val userId = async {
            try {
                client.graphQLGet(
                    apiUrl,
                    apiHeaders,
                    operationName = "cosmo_getCacheBusterUserId",
                    extensions = persistedQueryExtension(USER_ID_QUERY_HASH),
                ).parseGraphQLAs<UserResponse>().unextUser.id
            } catch (_: GraphQLException) {
                ""
            }
        }

        val playlistResult = client.graphQLGet(
            apiUrl,
            apiHeaders,
            operationName = "cosmo_getBookPlaylistUrl",
            variables = PageListVariables(bookFileCode),
            extensions = persistedQueryExtension(PLAYLIST_QUERY_HASH),
            cacheControl = FORCE_NETWORK,
        ).parseAs<PlaylistResponse>()

        val playlist = playlistResult.playlist ?: throw Exception(
            when (playlistResult.errorCode) {
                "BKE0004103" -> "This product can only be read in the U-NEXT app."
                "BKE0000467" -> "This service can only be used from Japan."
                else -> "Log in via WebView and rent or purchase this product to read."
            },
        )

        val contentKeys = async { getContentKeys(playlist, bookFileCode, userId.await()) }

        val zipUrl = playlist.zipUrl
        val entries = client.zipDirectory(zipUrl).entries.associateBy(Entry::name)
        val index = async { client.readZipEntry(zipUrl, entries.getValue("index.json")).buffer().parseAs<UBookIndex>() }
        val drm = async { client.readZipEntry(zipUrl, entries.getValue("drm.json")).buffer().parseAs<UBookDrm>() }

        val encryptedFiles = drm.await().encryptedFileList
        val keys = contentKeys.await()
        val pages = index.await()

        pages.spine.mapIndexed { i, spine ->
            val name = pages.pages.getValue(spine.pageId).image.src
            val file = encryptedFiles.getValue(name)
            val entry = entries.getValue(name)
            val data = ImageRequestData(
                localHeaderOffset = entry.localHeaderOffset,
                compressedSize = entry.compressedSize,
                method = entry.method,
                key = keys.getValue(file.keyId).toBase64(),
                iv = file.iv,
                originalFileSize = file.originalFileSize,
            )

            Page(i, imageUrl = "$zipUrl#${data.toJsonString()}")
        }
    }

    private suspend fun getContentKeys(
        playlist: Playlist,
        bookFileCode: String,
        userId: String,
    ): Map<String, ByteArray> {
        val challenge = ChallengeRequest(
            version = 1,
            playToken = playlist.playToken,
            nonce = Random.nextBytes(16).toBase64(),
            kek = keyPair.public.encoded.toBase64(),
            profile = "ubook",
        ).toJsonString().toByteArray().toBase64()

        val signature = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(SIGNING_KEY, algorithm))
            doFinal(challenge.toByteArray()).toHexString()
        }

        val url = playlist.licenseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("play_token", playlist.playToken)
            .build()

        val license = client.post(url, LicenseRequest(challenge, signature).toJsonRequestBody()).parseAs<LicenseResponse>().license
        val licenseKey = MessageDigest.getInstance("SHA-256")
            .digest((userId + bookFileCode).toByteArray())
            .copyOf(AES_KEY_SIZE)

        val data = Base64.decode(license.data, Base64.DEFAULT)
        val decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding")
        decrypt.init(Cipher.DECRYPT_MODE, SecretKeySpec(licenseKey, "AES"), IvParameterSpec(Base64.decode(license.iv, Base64.DEFAULT)))
        val records = decrypt.doFinal(data, HEADER_SIZE, data.size - HEADER_SIZE - DIGEST_SIZE)
        val unwrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        unwrap.init(Cipher.DECRYPT_MODE, keyPair.private, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))

        val version = data.readIntLittleEndian(VERSION_OFFSET)
        val stride = KEY_ID_SIZE + WRAPPED_KEY_SIZE + if (version == 0) 0 else KEY_VALIDITY_SIZE
        var offset = if (version == 0) LICENSE_VALIDITY_SIZE else 0

        return buildMap {
            repeat(data.readIntLittleEndian(KEY_COUNT_OFFSET)) {
                val keyId = String(records, offset, KEY_ID_SIZE, Charsets.US_ASCII).trimEnd('\u0000')
                put(keyId, unwrap.doFinal(records, offset + KEY_ID_SIZE, WRAPPED_KEY_SIZE))
                offset += stride
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "HIDE_PAID"

        // Monitor network requests to get hashes.
        // https://video.unext.jp/book/categoryranking/D_C_COMIC?genre=freecomic
        // https://cc.unext.jp/?operationName=cosmo_getBookRanking&variables={"targetCode":"D_C_COMIC","page":1,"pageSize":20}&extensions={"persistedQuery":{"version":1,"sha256Hash":"1e1e84fd9b5718c37ef030ea8230bbf9ddd1e5b86f5b8ce2c224b3704f0468ec"}}
        private const val POPULAR_QUERY_HASH = "1e1e84fd9b5718c37ef030ea8230bbf9ddd1e5b86f5b8ce2c224b3704f0468ec"

        // https://video.unext.jp/book/newarrivals/freecomic
        // https://cc.unext.jp/?operationName=cosmo_getNewBooks&variables={"tagCode":"TAG0000014500","page":1,"pageSize":20}&extensions={"persistedQuery":{"version":1,"sha256Hash":"0570a586caa9869bd5eb0b05a59bdfec853f92dc6cf280ebd583df2cc93e1c21"}}
        private const val LATEST_QUERY_HASH = "0570a586caa9869bd5eb0b05a59bdfec853f92dc6cf280ebd583df2cc93e1c21"

        // https://video.unext.jp/freeword/book?query=%E3%81%AE%E3%81%AE
        // https://cc.unext.jp/?zxuid=b2b5221e77d4&zxemp=29455178&operationName=cosmo_bookFreewordSearch&variables={"query":"のの","page":1,"pageSize":20,"filterSaleType":null,"sortOrder":"RECOMMEND"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"2ec7804350bf993678c92a5d79f20812b3b0d5b38aaba2603c9dd291c6df927e"}}
        private const val SEARCH_QUERY_HASH = "2ec7804350bf993678c92a5d79f20812b3b0d5b38aaba2603c9dd291c6df927e"

        // https://video.unext.jp/book/title/BSD0000820098
        // https://cc.unext.jp/?operationName=cosmo_bookTitleDetail&variables={"bookSakuhinCode":"BSD0000820098","viewBookCode":"TOTAL","bookListPageSize":2,"bookListChapterPageSize":5}&extensions={"persistedQuery":{"version":1,"sha256Hash":"99f21ebea20b64b11ef5d3b811c2b3fa5b4dbd8c5d2933baadf9c26fc60b35d1"}}
        private const val DETAILS_QUERY_HASH = "99f21ebea20b64b11ef5d3b811c2b3fa5b4dbd8c5d2933baadf9c26fc60b35d1"

        // https://video.unext.jp/book/title/BSD0000820098?bel=true&epi=0
        // https://cc.unext.jp/?operationName=cosmo_bookTitleBooks&variables={"bookSakuhinCode":"BSD0000820098","booksPage":1,"booksPageSize":9999}&extensions={"persistedQuery":{"version":1,"sha256Hash":"66f0c600259b82a4826fba7be2ace33726f3ec09735e65a421f8f602b481487d"}}
        private const val CHAPTER_LIST_QUERY_HASH = "66f0c600259b82a4826fba7be2ace33726f3ec09735e65a421f8f602b481487d"

        // https://video.unext.jp/book/view/BSD0000820098/BID0001508570
        // https://cc.unext.jp/?operationName=cosmo_getBookPlaylistUrl&variables={"bookFileCode":"BFC0002699405"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"f8a851c14ec61eb42dff966570b2ad49f86eeec7f39d2d32ab0ec58cad268fc1"}}
        private const val PLAYLIST_QUERY_HASH = "f8a851c14ec61eb42dff966570b2ad49f86eeec7f39d2d32ab0ec58cad268fc1"

        // https://cc.unext.jp/?operationName=cosmo_getCacheBusterUserId&extensions={"persistedQuery":{"version":1,"sha256Hash":"cadd8a9d8e909793bd96aa71cde95c0d3fb9b2dd0e78f91b1cf625d00f5b3553"}}
        private const val USER_ID_QUERY_HASH = "cadd8a9d8e909793bd96aa71cde95c0d3fb9b2dd0e78f91b1cf625d00f5b3553"

        private const val HEADER_SIZE = 16
        private const val VERSION_OFFSET = 8
        private const val KEY_COUNT_OFFSET = 12
        private const val DIGEST_SIZE = 65
        private const val LICENSE_VALIDITY_SIZE = 24
        private const val KEY_VALIDITY_SIZE = 16
        private const val KEY_ID_SIZE = 25
        private const val WRAPPED_KEY_SIZE = 256
        private const val AES_KEY_SIZE = 16

        private val SIGNING_KEY = "0f4b69c8094fb3ea927058ea17c061b74e5ff7c8b67f055793ce9e2e0d211352".decodeHex()
    }
}

private fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
