package eu.kanade.tachiyomi.extension.pt.randomscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MainPage(
    @SerialName("lancamentos")
    val latest: List<MangaDto>,
    @SerialName("top_10")
    val top10: List<MangaDto>,
)

@Serializable
class MangaDto(
    val title: String? = null,
    val titulo: String? = null,
    val capa: String,
    val slug: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@MangaDto.title ?: titulo.orEmpty()
        thumbnail_url = "$baseUrl$capa"
        url = "/$slug/"
    }
}

@Serializable
class SearchResponse(
    val obras: List<MangaDto>,
)

@Serializable
class MangaDetail(
    val capa: String,
    val titulo: String,
    val autor: String?,
    val artista: String?,
    val status: String,
    val sinopse: String,
    val tipo: String,
    val generos: List<Genero>,
    val caps: List<Capitulo>,
)

@Serializable
class Genero(
    val name: String,
)

@Serializable
class Capitulo(
    val num: Double,
    val data: String,
    val slug: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = slug
        name = num.toString().removeSuffix(".0")
        date_upload = Instant.tryParse(data)
        memo = buildJsonObject {
            put("mangaSlug", mangaSlug)
        }
    }
}

@Serializable
class Obra(
    val id: Int,
)

@Serializable
class User(
    @SerialName("user_authenticated")
    val authorized: Boolean,
    val userid: Long? = null,
)

@Serializable
class CapituloPagina(
    val id: Int,
    val obra: Obra,
    val files: Int,
)
