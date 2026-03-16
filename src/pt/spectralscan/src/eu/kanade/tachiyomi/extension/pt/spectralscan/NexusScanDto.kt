package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

// ==================== API Response DTOs ====================

@Serializable
class MangaListResponse(
    val data: List<MangaListDto>? = null,
    val page: Int = 1,
    val pages: Int = 1,
)

@Serializable
class MangaListDto(
    val slug: String,
    val title: String,
    val coverImage: String? = null,
)

@Serializable
class MangaDetailsDto(
    val slug: String,
    val title: String,
    val description: String? = null,
    val coverImage: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String,
    val categories: List<CategoryDto> = emptyList(),
    val chapters: List<ChapterDto>? = null,
)

@Serializable
class CategoryDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val id: Int,
    val number: String,
    val title: String? = null,
    val createdAt: String,
)

@Serializable
class ReadResponse(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val imageUrl: String,
    val pageNumber: Int,
)

@Serializable
class EncryptedResponse(
    val d: String,
    val k: Int = 0,
    val v: Int,
)

// ==================== Conversion Functions ====================

fun MangaListDto.toSManga() = SManga.create().apply {
    url = "/manga/$slug"
    title = this@toSManga.title
    thumbnail_url = coverImage
}

fun MangaDetailsDto.toSManga() = SManga.create().apply {
    url = "/manga/$slug"
    title = this@toSManga.title
    thumbnail_url = coverImage
    description = this@toSManga.description
    author = this@toSManga.author
    artist = this@toSManga.artist
    status = this@toSManga.status.parseStatus()
    genre = categories.joinToString { it.name }
}

fun ChapterDto.toSChapter(mangaSlug: String) = SChapter.create().apply {
    url = "/read/$id/$mangaSlug"
    name = if (!title.isNullOrBlank()) {
        "$title $number"
    } else {
        "Capítulo ${number.removeSuffix(".0")}"
    }
    date_upload = dateFormat.tryParse(createdAt.substringBefore("."))
}

private fun String?.parseStatus() = when (this?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

// ==================== Filters ====================

class SelectFilter(
    displayName: String,
    val parameter: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun selected() = vals[state].second
}

class CheckboxGroup(
    displayName: String,
    val parameter: String,
    val items: List<CheckboxItem>,
) : Filter.Group<CheckboxItem>(displayName, items) {
    fun selected() = items.filter { it.state }.map { it.value }
}

class CheckboxItem(
    displayName: String,
    val value: String,
) : Filter.CheckBox(displayName, false)

val sortList = arrayOf(
    "Visualizações" to "views",
    "Atualização" to "updatedAt",
    "Adicionado" to "created",
    "Título" to "title",
    "Avaliações" to "rating",
    "Capitulos" to "chapters",
    "Ano" to "releaseYear",
)

val orderList = arrayOf(
    "Decrescente" to "desc",
    "Crescente" to "asc",
)

val statusList = arrayOf(
    Pair("Em Andamento", "ongoing"),
    Pair("Completo", "completed"),
    Pair("Cancelado", "cancelled"),
    Pair("Hiato", "hiatus"),
)

val typeList = arrayOf(
    Pair("Manga", "manga"),
    Pair("Manhwa", "manhwa"),
    Pair("Manhua", "manhua"),
    Pair("Webtoon", "webtoon"),
    Pair("Comic", "comic"),
    Pair("HQ", "hq"),
    Pair("Pornhwa", "pornhwa"),
)

val categoryModeList = arrayOf(
    "Qualquer categoria (OU)" to "or",
    "Todas categorias (E)" to "and",
)

val genreList = arrayOf(
    Pair("Academia de Magia", "academia-de-magia"),
    Pair("Acadêmica", "academica"),
    Pair("Ação", "acao"),
    Pair("Adaptação", "adaptacao"),
    Pair("Adaptação de Novel", "adaptacao-de-novel"),
    Pair("Adultério", "adulterio"),
    Pair("Adulto", "adulto"),
    Pair("Ahegao", "ahegao"),
    Pair("Apocalipse", "apocalipse"),
    Pair("App", "app"),
    Pair("Artes Marciais", "artes-marciais"),
    Pair("Aventura", "aventura"),
    Pair("BDSM", "bdsm"),
    Pair("Bondage", "bondage"),
    Pair("Bullying", "bullying"),
    Pair("Bunda Grande", "bunda-grande"),
    Pair("Campus", "campus"),
    Pair("Cartoon", "cartoon"),
    Pair("Casada", "casada"),
    Pair("Casamento", "casamento"),
    Pair("Club", "club"),
    Pair("Comédia", "comedia"),
    Pair("Comédia Romântica", "comedia-romantica"),
    Pair("Comida", "comida"),
    Pair("Cotidiano", "cotidiano"),
    Pair("Crime", "crime"),
    Pair("Culinária", "culinaria"),
    Pair("Cultivo", "cultivo"),
    Pair("Curta", "curta"),
    Pair("Cyberpunk", "cyberpunk"),
    Pair("Dark Romance", "dark-romance"),
    Pair("Demônio", "demonio"),
    Pair("Demônios", "demonios"),
    Pair("Dominação", "dominacao"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Dragões", "dragoes"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Escolar", "escolar"),
    Pair("Escritório", "escritorio"),
    Pair("Esporte", "esporte"),
    Pair("Esportes", "esportes"),
    Pair("Estratégia", "estrategia"),
    Pair("Estudante", "estudante"),
    Pair("Exibicionismo", "exibicionismo"),
    Pair("Família", "familia"),
    Pair("Família Real", "familia-real"),
    Pair("Fantasia", "fantasia"),
    Pair("Fantasmas", "fantasmas"),
    Pair("Fetiche", "fetiche"),
    Pair("Ficção Científica", "ficcao-cientifica"),
    Pair("Futanari", "futanari"),
    Pair("Game", "game"),
    Pair("Game System", "game-system"),
    Pair("Gangster", "gangster"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Gênio", "genio"),
    Pair("Gore", "gore"),
    Pair("Guerra", "guerra"),
    Pair("Hardcore", "hardcore"),
    Pair("Harem", "harem"),
    Pair("Harém Reverso", "harem-reverso"),
    Pair("Hentai", "hentai"),
    Pair("Híbrido", "hibrido"),
    Pair("História", "historia"),
    Pair("Histórico", "historico"),
    Pair("Horror", "horror"),
    Pair("Idols", "idols"),
    Pair("Incesto", "incesto"),
    Pair("Isekai", "isekai"),
    Pair("Jogo", "jogo"),
    Pair("Jogos", "jogos"),
    Pair("Josei", "josei"),
    Pair("Luta", "luta"),
    Pair("Máfia", "mafia"),
    Pair("Magia", "magia"),
    Pair("Magia Escolar", "magia-escolar"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Médico", "medico"),
    Pair("Metaverso", "metaverso"),
    Pair("Milf", "milf"),
    Pair("Militar", "militar"),
    Pair("Mistério", "misterio"),
    Pair("Mitologia", "mitologia"),
    Pair("MMORPG", "mmorpg"),
    Pair("Monstros", "monstros"),
    Pair("Murim", "murim"),
    Pair("Música", "musica"),
    Pair("Musical", "musical"),
    Pair("Nerd", "nerd"),
    Pair("Netori", "netori"),
    Pair("Obsessão", "obsessao"),
    Pair("One Shot", "one-shot"),
    Pair("Oneshot", "oneshot"),
    Pair("Peitões", "peitoes"),
    Pair("Perda de Memória", "perda-de-memoria"),
    Pair("Polícia", "policia"),
    Pair("Policial", "policial"),
    Pair("Portais", "portais"),
    Pair("Pós-apocalíptico", "pos-apocaliptico"),
    Pair("Profecias", "profecias"),
    Pair("Prostituição", "prostituicao"),
    Pair("Psicológico", "psicologico"),
    Pair("Psicopata", "psicopata"),
    Pair("Realidade Virtual", "realidade-virtual"),
    Pair("Realismo", "realismo"),
    Pair("Reencarnação", "reencarnacao"),
    Pair("Reencontro", "reencontro"),
    Pair("Regressão", "regressao"),
    Pair("Retornado", "retornado"),
    Pair("Retorno", "retorno"),
    Pair("Revenge", "revenge"),
    Pair("Romance", "romance"),
    Pair("RPG", "rpg"),
    Pair("Sangue", "sangue"),
    Pair("School Life", "school-life"),
    Pair("Sci-Fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Sem Censura", "sem-censura"),
    Pair("Shoujo", "shoujo"),
    Pair("Shounen", "shounen"),
    Pair("Sistema", "sistema"),
    Pair("Sistema de Níveis", "sistema-de-niveis"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Sobrenatural", "sobrenatural"),
    Pair("Sobrevivência", "sobrevivencia"),
    Pair("Sombrio", "sombrio"),
    Pair("Sports", "sports"),
    Pair("Submissão", "submissao"),
    Pair("Sugestivo", "sugestivo"),
    Pair("Super Poderes", "super-poderes"),
    Pair("Supernatural", "supernatural"),
    Pair("Superpoderes", "superpoderes"),
    Pair("Suspense", "suspense"),
    Pair("Tecnológico", "tecnologico"),
    Pair("Tela de Sistema", "tela-de-sistema"),
    Pair("Terror", "terror"),
    Pair("Thriller", "thriller"),
    Pair("Tomboy", "tomboy"),
    Pair("Torre", "torre"),
    Pair("Tóxico", "toxico"),
    Pair("Tragédia", "tragedia"),
    Pair("Traição", "traicao"),
    Pair("Triângulo", "triangulo"),
    Pair("Tsundere", "tsundere"),
    Pair("Universitários", "universitarios"),
    Pair("Vampiro", "vampiro"),
    Pair("Vampiros", "vampiros"),
    Pair("Viagem no Tempo", "viagem-no-tempo"),
    Pair("Vida Adulta", "vida-adulta"),
    Pair("Vida Cotidiana", "vida-cotidiana"),
    Pair("Vida Escolar", "vida-escolar"),
    Pair("Video Game", "video-game"),
    Pair("Video Games", "video-games"),
    Pair("Vídeo Game", "video-game-1"),
    Pair("Vingança", "vinganca"),
    Pair("Violência", "violencia"),
    Pair("Volta no Tempo", "volta-no-tempo"),
    Pair("VRMMO", "vrmmo"),
    Pair("Web Comic", "web-comic"),
    Pair("Webcomic", "webcomic"),
    Pair("Wuxia", "wuxia"),
    Pair("Xianxia", "xianxia"),
    Pair("Xuanhuan", "xuanhuan"),
    Pair("Yuri", "yuri"),
    Pair("Zumbis", "zumbis"),
)

val themeList = arrayOf(
    Pair("Animais", "animais"),
    Pair("Artes Marciais", "artes-marciais-tema"),
    Pair("Culinária", "culinaria-tema"),
    Pair("Esporte", "esporte-tema"),
    Pair("Fantasia", "fantasia-tema"),
    Pair("Ficção Científica", "ficcao-cientifica-tema"),
    Pair("Histórico", "historico-tema"),
    Pair("Isekai", "isekai-tema"),
    Pair("Jogo", "jogo-tema"),
    Pair("Militar", "militar-tema"),
    Pair("Monstros", "monstros-tema"),
    Pair("Música", "musica-tema"),
    Pair("Reencarnação", "reencarnacao-tema"),
    Pair("Robôs", "robos"),
    Pair("Sobrenatural", "sobrenatural-tema"),
    Pair("Super Herói", "super-heroi"),
    Pair("Vampiros", "vampiros-tema"),
    Pair("Vilão", "vilao"),
    Pair("Virtual Reality", "virtual-reality"),
    Pair("Zumbis", "zumbis-tema"),
)
