package eu.kanade.tachiyomi.extension.pt.yaoifanclub

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class YaoiFanClub : ZeistManga(
    "Yaoi Fan Club",
    "https://www.yaoifanclub.com",
    "pt-BR",
) {

    override val popularMangaSelector = "#PopularPosts3 article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override val useNewChapterFeed = true
    override val chapterCategory = "Chapter"

    override val hasFilters = true
    override val hasLanguageFilter = false
    override val hasGenreFilter = true
    override val hasStatusFilter = true

    override fun headersBuilder() =
        super.headersBuilder()
            .set("Referer", "https://www.blogger.com/blogin.g?blogspotURL=$baseUrl/&type=blog&bpli=1")

    override fun getGenreList(): List<Genre> = listOf(
        Genre("ABO", "ABO"),
        Genre("Ação", "Ação"),
        Genre("Anjo", "Anjo"),
        Genre("Apocalipse", "Apocalipse"),
        Genre("Aventura", "Aventura"),
        Genre("Comédia", "Comédia"),
        Genre("Drama", "Drama"),
        Genre("Demência", "Demência"),
        Genre("Demônio", "Demônio"),
        Genre("Espaço", "Espaço"),
        Genre("Esporte", "Esporte"),
        Genre("Fantasma", "Fantasma"),
        Genre("Fantasia", "Fantasia"),
        Genre("Ficção", "Ficção"),
        Genre("Game", "Game"),
        Genre("Gore", "Gore"),
        Genre("Harem", "Harem"),
        Genre("Histórico", "Histórico"),
        Genre("Horror", "Horror"),
        Genre("Magia", "Magia"),
        Genre("Militar", "Militar"),
        Genre("Mistério", "Mistério"),
        Genre("Música", "Música"),
        Genre("Omegaverso", "Omegaverso"),
        Genre("Paródia", "Paródia"),
        Genre("Poderes", "Poderes"),
        Genre("Policial", "Policial"),
        Genre("Psicológico", "Psicológico"),
        Genre("Robô", "Robô"),
        Genre("Romance", "Romance"),
        Genre("Samurai", "Samurai"),
        Genre("Sobrenatural", "Sobrenatural"),
        Genre("Suspense", "Suspense"),
        Genre("Terror", "Terror"),
        Genre("Vampiro", "Vampiro"),
        Genre("Viagem no tempo", "Viagem no tempo"),
        Genre("Vida Cotidiana", "Vida Cotidiana"),
        Genre("Zumbi", "Zumbi"),
    )

    override fun getTypeList(): List<Type> = listOf(
        Type("Todos", ""),
        Type("Comic", "Comic"),
        Type("Doujinshi", "Doujinshi"),
        Type("Manga", "Manga"),
        Type("Manhua", "Manhua"),
        Type("Manhwa", "Manhwa"),
        Type("Oneshot", "Oneshot"),
        Type("Anime", "Anime"),

    )
    override fun getStatusList(): List<Status> = listOf(
        Status("Ativo", "Ativo"),
        Status("Completo", "Completo"),
        Status("Dropado", "Dropado"),
        Status("Em Breve", "Em Breve"),
    )
}
