package eu.kanade.tachiyomi.extension.vi.moetruyen

import keiyoushi.network.post
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.net.URLDecoder

internal class ImgxAccessClient(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val chapterUrl: String,
    private val document: Document,
) {
    private val keyPair = ImgxCrypto.generateEcdhP256()
    private var sequence = 0L

    suspend fun fetchPages(
        media: List<ReaderMediaEntry>,
        totalPages: Int = 0,
    ): List<ImgxPageAccess> {
        val config = parseBootstrapConfig(document)

        val pageIndexes = if (media.isNotEmpty()) {
            media.map { it.pageIndex }
        } else {
            (0 until totalPages).toList()
        }

        if (config.bootstrapUrl.isBlank()) {
            throw IllegalStateException(
                "IMGX document capability required — site withheld reader-instance token " +
                    "(path=${config.requestPath} chapterId=${config.chapterId} pages=${pageIndexes.size})",
            )
        }

        val bootstrapProof = ImgxCrypto.base64UrlEncode(ImgxCrypto.randomBytes(32))
        val bootstrap = client.post(
            "$baseUrl${config.bootstrapUrl}",
            jsonHeaders(),
            BootstrapRequest(
                readerPublicKey = keyPair.publicKey,
                bootstrapProof = bootstrapProof,
                initialPageIndexes = config.initialIndexes,
            ).toJsonRequestBody(),
        ).parseAs<BootstrapResponse>()

        require(bootstrap.ok && bootstrap.sealedCapability != null) {
            "IMGX bootstrap failed: ${bootstrap.code ?: "unknown"}"
        }

        val capability = ImgxCrypto.openSealedCapability(keyPair, bootstrap.sealedCapability, bootstrapProof)
        require(capability.readerInstanceId == bootstrap.readerInstanceId) {
            "IMGX reader instance mismatch"
        }

        val granted = mutableMapOf<Int, ImgxPageAccess>()
        if (bootstrap.sealedInitialPages != null) {
            ImgxCrypto.openSealedPages(keyPair, bootstrap.sealedInitialPages, bootstrapProof).forEach { page ->
                granted[page.pageIndex] = page
            }
        }

        val remaining = pageIndexes.filterNot { it in granted }
        remaining.chunked(10).forEach { indexes ->
            sequence += 1
            val material = buildProofMaterial(
                readerInstanceId = capability.readerInstanceId,
                chapterId = config.chapterId,
                requestPath = config.requestPath,
                pageIndexes = indexes,
                issuedAt = bootstrap.serverTime,
                sequence = sequence,
                publicKeyHash = ImgxCrypto.publicKeyHash(keyPair.publicKey),
            )
            val signature = ImgxCrypto.base64UrlEncode(
                ImgxCrypto.hmacSha256(ImgxCrypto.base64UrlDecode(capability.secret), material),
            )
            val response = client.post(
                "$baseUrl${config.requestPath}",
                jsonHeaders(),
                PageAccessRequest(
                    pageIndexes = indexes,
                    pageAccessProof = PageAccessProof(
                        version = PROOF_VERSION,
                        readerInstanceId = capability.readerInstanceId,
                        issuedAt = bootstrap.serverTime,
                        sequence = sequence,
                        proof = signature,
                    ),
                    readerPublicKey = keyPair.publicKey,
                ).toJsonRequestBody(),
            ).parseAs<PageAccessResponse>()

            require(response.ok && response.sealedPages != null) {
                "IMGX page access failed: ${response.code ?: "unknown"}"
            }

            ImgxCrypto.openSealedPages(keyPair, response.sealedPages, signature).forEach { page ->
                granted[page.pageIndex] = page
            }
        }

        return if (media.isNotEmpty()) {
            media.mapNotNull { entry -> granted[entry.pageIndex] }
        } else {
            pageIndexes.mapNotNull { idx -> granted[idx] }
        }
    }

    private fun jsonHeaders(): Headers = Headers.Builder()
        .set("Accept", "application/json")
        .set("Content-Type", "application/json")
        .set("Origin", baseUrl)
        .set("Referer", chapterUrl)
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-origin")
        .build()

    companion object {
        private const val PROOF_VERSION = "imgx-page-access-proof-v3"

        fun parseBootstrapConfig(document: Document): ReaderBootstrapConfig {
            val allText = document.select("script").joinToString("\n") { it.data() } +
                "\n" + document.html()

            val requestPath = Regex("""requestPath:\s*"([^"]+)"""").find(allText)?.groupValues?.get(1)
            val chapterId = Regex("""chapterId:\s*(\d+)""").find(allText)?.groupValues?.get(1)?.toLongOrNull()
            val bootstrapUrl = Regex("""bootstrapUrl:\s*"([^"]*)"""").find(allText)?.groupValues?.get(1)
                .orEmpty()
            val initialIndexes = Regex("""initialIndexes:\s*\[([^\]]*)\]""").find(allText)
                ?.groupValues
                ?.get(1)
                ?.split(',')
                ?.mapNotNull { it.trim().takeIf { v -> v.isNotEmpty() }?.toInt() }
                .orEmpty()

            if (requestPath != null && chapterId != null) {
                return ReaderBootstrapConfig(requestPath, chapterId, bootstrapUrl, initialIndexes)
            }

            val root = document.selectFirst("[data-reader-lazy-pages]")
                ?: throw IllegalStateException("IMGX reader metadata missing")
            val accessUrl = root.attr("data-reader-imgx-access-url")
            if (accessUrl.isBlank()) throw IllegalStateException("IMGX access URL missing")
            val trackToken = root.attr("data-reader-view-track-token")
            val attrChapterId = trackToken.substringBefore('.').toLongOrNull()
                ?: chapterId
                ?: throw IllegalStateException("IMGX chapter id missing")
            val totalPages = root.attr("data-reader-total-pages").toIntOrNull() ?: 0
            return ReaderBootstrapConfig(
                requestPath = accessUrl,
                chapterId = attrChapterId,
                bootstrapUrl = bootstrapUrl,
                initialIndexes = if (totalPages > 0) listOf(totalPages - 1) else emptyList(),
            )
        }

        fun buildProofMaterial(
            readerInstanceId: String,
            chapterId: Long,
            requestPath: String,
            pageIndexes: List<Int>,
            issuedAt: Long,
            sequence: Long,
            publicKeyHash: String,
        ): ByteArray {
            val indexes = pageIndexes.joinToString(",", prefix = "[", postfix = "]")
            return """["$PROOF_VERSION","$readerInstanceId",$chapterId,"$requestPath","",$indexes,$issuedAt,$sequence,"$publicKeyHash"]"""
                .toByteArray(Charsets.UTF_8)
        }

        fun encryptedMedia(document: Document): List<ReaderMediaEntry> {
            val root = document.selectFirst("[data-reader-lazy-pages]")
            val mediaJson = root?.attr("data-reader-imgx-media").orEmpty()
            val decoded = if (mediaJson.isBlank()) {
                // Some chapters only embed media in the worker INIT script.
                val scriptMedia = Regex("""media:\s*(\[[\s\S]*?\])\s*,\s*\n\s*connection:""")
                    .find(document.html())
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
                scriptMedia
            } else {
                runCatching { URLDecoder.decode(mediaJson, Charsets.UTF_8.name()) }.getOrDefault(mediaJson)
            }
            if (decoded.isBlank()) return emptyList()
            val media = runCatching {
                decoded.parseAs<List<ReaderMediaEntry>>()
            }.getOrDefault(emptyList())
            return media.filter { entry ->
                entry.storageKey.startsWith("chapters/") &&
                    !entry.storageKey.endsWith("/0.js") &&
                    !entry.downloadUrl.endsWith("/0.js")
            }
        }
    }
}

@kotlinx.serialization.Serializable
private class BootstrapRequest(
    val readerPublicKey: String,
    val bootstrapProof: String,
    val initialPageIndexes: List<Int>,
)
