package eu.kanade.tachiyomi.extension.pt.taosect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaoSectProjectDto(
    val content: TaoSectContentDto? = null,
    val id: Int? = -1,
    @SerialName("informacoes") val info: TaoSectProjectInfoDto? = null,
    val link: String? = "",
    val slug: String? = "",
    val title: TaoSectContentDto? = null,
    val thumbnail: String? = "",
    @SerialName("capitulos") val volumes: List<TaoSectVolumeDto>? = emptyList(),
)

@Serializable
data class TaoSectContentDto(
    val rendered: String = "",
)

@Serializable
data class TaoSectProjectInfoDto(
    @SerialName("arte") val art: String = "",
    @SerialName("generos") val genres: List<TaoSectTagDto> = emptyList(),
    @SerialName("titulo_pais_origem") val originalTitle: String = "",
    @SerialName("roteiro") val script: String = "",
    @SerialName("serializacao") val serialization: String = "",
    @SerialName("status_scan") val status: TaoSectTagDto? = null,
)

@Serializable
data class TaoSectTagDto(
    @SerialName("nome") val name: String = "",
)

@Serializable
data class TaoSectVolumeDto(
    @SerialName("capitulos") val chapters: List<TaoSectChapterDto> = emptyList(),
)

@Serializable
data class TaoSectChapterDto(
    @SerialName("data_insercao") val date: String = "",
    @SerialName("id_capitulo") val id: String = "",
    @SerialName("nome_capitulo") val name: String = "",
    @SerialName("paginas") val pages: List<String> = emptyList(),
    @SerialName("post_id") val projectId: String? = "",
    val slug: String = "",
)
