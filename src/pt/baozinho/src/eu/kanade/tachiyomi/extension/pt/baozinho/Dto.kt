package eu.kanade.tachiyomi.extension.pt.baozinho

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Instant

private const val CDN_URL = "https://cdn.baozinho.com"

// Repeated tagIds combine with OR; a comma-separated list makes the API answer 500.
fun worksUrl(
    apiUrl: String,
    page: Int,
    orderBy: String,
    query: String = "",
    status: String = "",
    format: String = "",
    tagIds: List<Int> = emptyList(),
): HttpUrl = "$apiUrl/obras".toHttpUrl().newBuilder().apply {
    addQueryParameter("page", page.toString())
    addQueryParameter("orderBy", orderBy)
    query.trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter("q", it) }
    status.takeIf { it.isNotEmpty() }?.let { addQueryParameter("status", it) }
    format.takeIf { it.isNotEmpty() }?.let { addQueryParameter("formatoSlug", it) }
    tagIds.forEach { addQueryParameter("tagIds", it.toString()) }
}.build()

fun parseStatus(status: String?) = when (status) {
    "em_lancamento" -> SManga.ONGOING
    "finalizado" -> SManga.COMPLETED
    "hiato" -> SManga.ON_HIATUS
    "cancelado" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

// Filter data is cached on disk by the app, so the serial names must not change.
@Serializable
class TagDto(
    val id: Int,
    @SerialName("nome") val name: String,
    @SerialName("obrasCount") val worksCount: Int = 0,
)

@Serializable
class FormatDto(@SerialName("nome") val name: String, val slug: String)

@Serializable
class FiltersDto(
    val tags: List<TagDto>,
    @SerialName("formatos") val formats: List<FormatDto>,
) {
    fun tagsWithWorks() = tags.filter { it.worksCount > 0 }
}

@Serializable
class NameDto(@SerialName("nome") val name: String)

@Serializable
class WorksDto(
    val data: List<WorkDto>,
    private val page: Int,
    private val pages: Int,
) {
    val hasNextPage get() = page < pages

    fun toMangasPage() = MangasPage(data.map { it.toSManga() }, hasNextPage)
}

// Property names avoid SManga's own (title, description, author, artist, status) because they are used inside apply.
@Serializable
class WorkDto(
    val id: Int,
    @SerialName("nome") private val workTitle: String,
    private val slug: String,
    @SerialName("imagem") private val image: String? = null,
    @SerialName("descricao") private val synopsis: String? = null,
    @SerialName("titulosAlternativos") private val alternativeTitles: String? = null,
    @SerialName("status") private val statusText: String? = null,
    @SerialName("formato") private val format: NameDto? = null,
    @SerialName("autor") private val authorInfo: NameDto? = null,
    @SerialName("arte") private val artistName: String? = null,
    private val tags: List<NameDto> = emptyList(),
) {
    fun descriptionMarkdown() = MarkdownDescription.Builder()
        .description(synopsis)
        .altTitles(alternativeTitles.orEmpty().split("|"), "Nomes alternativos")
        .build()

    fun genres() = (listOfNotNull(format) + tags).joinToString { it.name }

    fun toSManga() = SManga.create().apply {
        url = slug
        title = workTitle
        thumbnail_url = image?.let { "$CDN_URL/$it" }
        author = authorInfo?.name
        artist = artistName
        description = descriptionMarkdown()
        genre = genres()
        status = parseStatus(statusText)
        // Chapters are listed by this numeric id, so keep it instead of refetching the details.
        memo = buildJsonObject { put("id", id.toString()) }
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("numero") private val number: Float,
    @SerialName("nome") private val chapterTitle: String,
    @SerialName("lancadoEm") private val releasedAt: String? = null,
    @SerialName("gratuito") private val free: Boolean = false,
) {
    fun isLockedFor(unlocked: Set<Int>) = !free && id !in unlocked

    fun displayName(unlocked: Set<Int> = emptySet()) = if (isLockedFor(unlocked)) "🔒 $chapterTitle" else chapterTitle

    val uploadDate get() = Instant.tryParse(releasedAt)

    fun path(slug: String) = "$slug/capitulo-${number.toString().removeSuffix(".0")}"

    fun toSChapter(slug: String, unlocked: Set<Int> = emptySet()) = SChapter.create().apply {
        url = path(slug)
        name = displayName(unlocked)
        chapter_number = number
        date_upload = uploadDate
        memo = buildJsonObject { put("id", id.toString()) }
    }
}

// unlocked == null: logged in but the unlocked list failed; better to show too much than hide what the account owns.
fun List<ChapterDto>.visible(hidePaid: Boolean, unlocked: Set<Int>? = emptySet()) = if (hidePaid && unlocked != null) filterNot { it.isLockedFor(unlocked) } else this

@Serializable
class ChapterPagesDto(
    @SerialName("paginas") private val pages: List<PageDto> = emptyList(),
) {
    fun toPages() = pages.sortedBy { it.order }.mapIndexed { index, page -> Page(index, imageUrl = page.url) }
}

@Serializable
class PageDto(
    val url: String,
    @SerialName("ordem") val order: Int,
)

@Serializable
class TokensDto(val accessToken: String, val refreshToken: String)

@Serializable
class RefreshRequest(val refreshToken: String)

@Serializable
class UnlockedDto(@SerialName("capId") val chapterId: Int)

@Serializable
class UserDto(@SerialName("nome") val name: String? = null)

@Serializable
class LoginRequest(
    val email: String,
    @SerialName("senha") val password: String,
    val turnstileToken: String,
)

// Only a clear rejection drops the session; network errors, 5xx and 429 keep the stored token.
fun isSessionRejected(code: Int, message: String?) = code == 401 || code == 403 || (code == 400 && message?.contains("token", ignoreCase = true) == true)

enum class LoginFailure { CREDENTIALS, VERIFICATION, OTHER }

private val verificationRegex = Regex("turnstile|captcha|verifica", RegexOption.IGNORE_CASE)

// So the user knows what to fix: e-mail/password, the site's verification, or something else (network, server, rate limit).
fun classifyLoginFailure(code: Int, message: String?) = when {
    code !in 400..403 -> LoginFailure.OTHER
    message?.let(verificationRegex::containsMatchIn) == true -> LoginFailure.VERIFICATION
    else -> LoginFailure.CREDENTIALS
}

// A failed login is not retried for a while, so the captcha is not reopened on every request.
fun shouldRetryLogin(lastFailureAt: Instant?, now: Instant, retryAfter: Duration) = lastFailureAt == null || now - lastFailureAt >= retryAfter

// Only a rejection the user can fix gets a custom message; any other status is a plain HttpException.
fun loginError(code: Int, message: String?): Exception = when (classifyLoginFailure(code, message)) {
    LoginFailure.CREDENTIALS -> IOException("E-mail ou senha do Baozinho incorretos. Confira nas configurações da extensão.")
    LoginFailure.VERIFICATION -> IOException("O Baozinho recusou a verificação anti-robô. Tente de novo mais tarde.")
    LoginFailure.OTHER -> HttpException(code)
}

fun refreshError(code: Int): Exception = HttpException(code)

// null = unknown: with credentials but no session yet, nothing should be hidden.
fun unlockedWithoutSession(hasCredentials: Boolean): Set<Int>? = if (hasCredentials) null else emptySet()

fun needsDetails(fetchDetails: Boolean, knownId: Int?) = fetchDetails || knownId == null

// Free chapters never log in: only a chapter that came back empty, with no valid session, and with saved credentials does.
fun shouldLoginForPages(pagesEmpty: Boolean, hadSession: Boolean, hasCredentials: Boolean) = pagesEmpty && !hadSession && hasCredentials

fun passwordSummary(password: String, connected: Boolean) = if (password.isEmpty()) "Não definida" else "Definida (••••••••)" + if (connected) " · Conectado" else ""
