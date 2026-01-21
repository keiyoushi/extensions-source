package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class MangoResponse<T>(
    val sucesso: Boolean? = false,
    val dados: T? = null,
    val obras: T? = null,
    val obra: T? = null,
    val data: T? = null,
    val capitulos: T? = null,
    val pagination: PaginationDto? = null,
) {
    val items: T
        get() = dados ?: obras ?: obra ?: data ?: capitulos ?: throw Exception("Data field not found")
}

@Serializable
class PaginationDto(
    val hasNextPage: Boolean = false,
)

@Serializable
class MangoMangaDto(
    @JsonNames("id", "slug", "unique_id")
    val id: Int? = null,
    @SerialName("title")
    @JsonNames("nome")
    val titulo: String,
    @JsonNames("coverImage")
    val imagem: String,
    val descricao: String? = null,
    val status_nome: String? = null,
    val tags: List<MangoTagDto>? = null,
    val capitulos: List<MangoChapterDto>? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = titulo
        url = "/obra/$id"
        thumbnail_url = imagem?.let {
            it.takeIf { it.toHttpUrlOrNull() != null } ?: "$cdnUrl/$it"
        }

        description = descricao
        genre = tags?.joinToString { it.nome }
        status = when (status_nome) {
            "Ativo", "Em Andamento" -> SManga.ONGOING
            "Concluído" -> SManga.COMPLETED
            "Hiato", "Pausado" -> SManga.ON_HIATUS
            "Cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class MangoTagDto(
    val nome: String,
)

@Serializable
class MangoChapterDto(
    val obra_id: Int,
    val numero: Float,
    @SerialName("criado_em") val data: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        name = "Capítulo ${formatChapterNumber(numero)}"
        chapter_number = numero
        url = "/obra/$obra_id/capitulo/${formatChapterNumber(numero)}"
        date_upload = dateFormat.tryParse(data)
    }

    private fun formatChapterNumber(numero: Float): String {
        return numero.toString().removeSuffix(".0")
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

@Serializable
class MangoPageResponse(
    val sucesso: Boolean? = false,
    val capitulo: MangoPageChapterDto? = null,
)

@Serializable
class MangoPageChapterDto(
    @SerialName("obra_id") val obraId: Int,
    val numero: Int,
    val paginas: List<MangoPageDto> = emptyList(),
)

@Serializable
class MangoPageDto(
    val numero: Int,
    @SerialName("image_random_id") val imageRandomId: Long,
)

@Serializable
class MangoLatestChapterDto(
    val obra: MangoMangaDto? = null,
)

@Serializable
class LoginResponseDto(
    val sucesso: Boolean = false,
    val token: String? = null,
)

@Serializable
class AuthRequestDto(
    val email: String,
    val senha: String,
)
