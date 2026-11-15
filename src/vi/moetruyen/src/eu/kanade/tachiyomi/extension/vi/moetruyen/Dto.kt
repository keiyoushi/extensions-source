package eu.kanade.tachiyomi.extension.vi.moetruyen

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class ReaderMediaEntry(
    val pageIndex: Int,
    val storageKey: String,
    val downloadUrl: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
class SealedChannel(
    val version: String,
    val publicKey: String,
    val iv: String,
    val ciphertext: String,
)

@Serializable
class BootstrapResponse(
    val ok: Boolean,
    val chapterId: Long,
    val serverTime: Long,
    val readerInstanceId: String? = null,
    val sealedCapability: SealedChannel? = null,
    val sealedInitialPages: SealedChannel? = null,
    val code: String? = null,
)

@Serializable
class PageAccessProof(
    val version: String,
    val readerInstanceId: String,
    val issuedAt: Long,
    val sequence: Long,
    val proof: String,
)

@Serializable
class PageAccessRequest(
    val pageIndexes: List<Int>,
    val pageAccessProof: PageAccessProof,
    val readerPublicKey: String,
)

@Serializable
class PageAccessResponse(
    val ok: Boolean,
    val sealedPages: SealedChannel? = null,
    val code: String? = null,
)

@Serializable
class ImgxGrant(
    val version: Int? = null,
    val algorithm: String? = null,
    val imageId: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val nonce: String? = null,
    val keyNonce: String? = null,
    val signature: String? = null,
    val wrappedDecodeKey: String? = null,
    val wrappedContentKey: String? = null,
    val wrappedV4Key: String? = null,
    val decodeKey: String? = null,
    val channelKeys: ChannelKeys? = null,
)

@Serializable
class ChannelKeys(
    val version: String,
    val iv: String,
    val ciphertext: String,
)

@Serializable
class DecryptedChannelKeys(
    val decodeKey: String? = null,
    val wrappedDecodeKey: String? = null,
    val wrappedContentKey: String? = null,
    val wrappedV4Key: String? = null,
)

@Serializable
class ImgxPageAccess(
    val pageIndex: Int,
    val storageKey: String,
    val downloadUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val grant: ImgxGrant? = null,
)

@Serializable
data class ReaderBootstrapConfig(
    val requestPath: String,
    val chapterId: Long,
    val bootstrapUrl: String,
    val initialIndexes: List<Int>,
)

@Serializable
class ReaderCapability(
    val readerInstanceId: String,
    val chapterId: Long,
    val secret: String,
)

@Serializable
class CommentRequest(
    val content: String,
    val requestId: String,
)

@Serializable
class AuthSession(
    val session: JsonObject? = null,
)

@Serializable
class ApiError(
    val error: String? = null,
)
