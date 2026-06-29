package eu.kanade.tachiyomi.extension.vi.tuitruyen

import kotlinx.serialization.Serializable

@Serializable
class PageAccessRequest(
    val pageIndexes: List<Int>,
    val pageAccessProof: PageAccessProof? = null,
)

@Serializable
class PageAccessProof(
    val version: String,
    val token: String,
    val issuedAt: Long,
    val nonce: String,
    val proof: String,
)

@Serializable
class PageAccessResponse(
    val ok: Boolean,
    val pages: List<PageAccessEntry> = emptyList(),
    val maxWindow: Int = 5,
)

@Serializable
class PageAccessEntry(
    val pageIndex: Int,
    val storageKey: String = "",
    val downloadUrl: String = "",
    val grant: ImgxGrant? = null,
)

@Serializable
class ImgxGrant(
    val version: Int? = null,
    val algorithm: String? = null,
    val contentAlgorithm: String? = null,
    val imageId: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val nonce: String? = null,
    val keyNonce: String? = null,
    val signature: String? = null,
    val wrappedDecodeKey: String? = null,
    val wrappedContentKey: String? = null,
    val decodeKey: String? = null,
)
