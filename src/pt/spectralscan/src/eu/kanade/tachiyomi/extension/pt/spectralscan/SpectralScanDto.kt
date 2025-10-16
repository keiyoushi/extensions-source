package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SelectFilter(displayName: String = "", val parameter: String = "", private val vals: Array<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun selected() = vals[state].second
}

@Serializable
class ImageSrc(
    @SerialName("image_data")
    val base64: String = "",
    @SerialName("mime_type")
    val mimeType: String = "",
    @SerialName("image_url")
    val url: String = "",
) {
    fun isBase64() = base64.isNotBlank()
}

val sortList = arrayOf(
    "Mais Recentes" to "latest",
    "Mais Populares" to "popular",
    "Melhor Avaliação" to "rating",
    "Nome (A-Z)" to "name_asc",
    "Nome (Z-A)" to "name_desc",
)

val genreList = arrayOf(
    "Todos os Gêneros" to "",
    "Ação" to "acao",
    "Armas e Combate" to "armas-e-combate",
    "Artes Marciais" to "artes-marciais",
    "Aventura" to "aventura",
    "Comédia" to "comedia",
    "Comida" to "comida",
    "Cultivo" to "cultivo",
    "Cyberpunk" to "cyberpunk",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Escolar" to "escolar",
    "Espacial" to "espacial",
    "Esportes" to "esportes",
    "Fantasia" to "fantasia",
    "Ficção Científica" to "ficcao-cientifica",
    "Harem" to "harem",
    "Hentai" to "hentai",
    "Horror" to "horror",
    "Jogo" to "jogo",
    "Josei" to "josei",
    "Kodomomuke" to "kodomomuke",
    "Mature Themes" to "mature-themes",
    "Mecha" to "mecha",
    "Mistério" to "misterio",
    "Monstros" to "monstros",
    "Psicológico" to "psicologico",
    "Realidade Virtual" to "realidade-virtual",
    "Reencarnação" to "reencarnacao",
    "Regressão" to "regressao",
    "Romance" to "romance",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Sistema" to "sistema",
    "Slice of Life" to "slice-of-life",
    "Sobrenatural" to "sobrenatural",
    "Superpoderes" to "superpoderes",
    "Suspense" to "suspense",
    "Tela de Sistema" to "tela-de-sistema",
    "Tragédia" to "tragedia",
    "Vida Cotidiana" to "vida-cotidiana",
    "Volta no Tempo" to "volta-no-tempo",
)
