package eu.kanade.tachiyomi.extension.pt.mediocretoons

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private const val CDN_URL = "https://cdn.mediocrescan.com"

@Serializable
class LoginRequestDto(
    private val email: String,
    private val senha: String,
)

@Serializable
class LoginDto(
    val token: String? = null,
)

@Serializable
class ErrorDto(
    val message: String,
)

@Serializable
class MangaListDto(
    private val data: List<MangaDto>,
    private val pagination: PaginationDto,
) {
    fun toMangasPage() = MangasPage(data.map(MangaDto::toSManga), pagination.hasNextPage)

    @Serializable
    class PaginationDto(
        val hasNextPage: Boolean,
    )
}

@Serializable
class MangaDto(
    private val id: Int,
    private val nome: String,
    private val imagem: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        // Same url format as 1.4, so existing library entries and chapters keep matching
        url = "/obra/$id"
        title = nome
        thumbnail_url = imagem?.let { "$CDN_URL/obras/$id/$it" }
    }
}

@Serializable
class MangaDetailsDto(
    @SerialName("obr_id") private val id: Int,
    @SerialName("obr_nome") private val nome: String,
    @SerialName("obr_imagem") private val imagem: String? = null,
    @SerialName("obr_descricao") private val descricao: String? = null,
    @SerialName("obr_status") private val status: String? = null,
    private val tags: List<TagDto> = emptyList(),
    private val capitulos: List<ChapterDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$id"
        title = nome
        thumbnail_url = imagem?.let { "$CDN_URL/obras/$id/$it" }
        description = descricao
        genre = tags.joinToString { it.nome }
        this.status = when (this@MangaDetailsDto.status?.lowercase()) {
            "em_lancamento" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    fun toChapterList() = capitulos.map(ChapterDto::toSChapter).sortedByDescending(SChapter::chapter_number)

    @Serializable
    class TagDto(
        @SerialName("tag_nome") val nome: String,
    )

    @Serializable
    class ChapterDto(
        @SerialName("cap_id") private val id: Int,
        @SerialName("cap_nome") private val nome: String,
        @SerialName("cap_num") private val num: Float,
        @SerialName("cap_lancado_em") private val lancadoEm: String? = null,
        @SerialName("cap_criado_em") private val criadoEm: String? = null,
    ) {
        fun toSChapter() = SChapter.create().apply {
            url = "/capitulo/$id"
            name = nome
            chapter_number = num
            date_upload = Instant.tryParse(lancadoEm ?: criadoEm)
        }
    }
}

@Serializable
class ChapterDetailsDto(
    @SerialName("cap_uuid") private val uuid: String,
    @SerialName("cap_num") private val num: Float,
    @SerialName("obr_id") private val mangaId: Int,
) {
    val pageListUrl get() = "$CDN_URL/obras/$mangaId/capitulos/${num.toString().removeSuffix(".0")}/$uuid.json"
}

@Serializable
class PageDto(
    val ordem: Int,
    @SerialName("url") private val path: String,
) {
    val imageUrl get() = "$CDN_URL/$path"
}

fun List<PageDto>.toPageList() = sortedBy(PageDto::ordem).mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
