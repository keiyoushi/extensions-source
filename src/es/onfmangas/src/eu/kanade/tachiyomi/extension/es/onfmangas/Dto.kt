package eu.kanade.tachiyomi.extension.es.onfmangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    private val url: String,
    @SerialName("titulo_str") private val tituloStr: String? = null,
    private val numero: String? = null,
    @SerialName("fecha_subida") private val fechaSubida: String? = null,
    @SerialName("grupos_list") private val gruposList: List<GroupDto>? = null,
    @SerialName("otras_versiones") private val otrasVersiones: List<ChapterDto>? = null,
) {
    val date: String? get() = fechaSubida

    val numberFloat: Float get() = numero?.toFloatOrNull() ?: 0f

    fun toSChapter(parent: ChapterDto? = null) = SChapter.create().apply {
        val num = this@ChapterDto.numero ?: parent?.numero
        this.url = this@ChapterDto.url
        this.name = this@ChapterDto.tituloStr
            ?: parent?.tituloStr
            ?: num?.let { "Capítulo $it" }
            ?: "Capítulo sin número"
        this.chapter_number = this@ChapterDto.numberFloat
        this.scanlator = this@ChapterDto.gruposList?.joinToString(" & ") { it.nombre }
    }

    fun getOtherVersions() = otrasVersiones
}

@Serializable
class GroupDto(
    val nombre: String,
)

@Serializable
class PageDto(
    private val src: String,
) {
    fun toPage(index: Int) = Page(index, imageUrl = src)
}
