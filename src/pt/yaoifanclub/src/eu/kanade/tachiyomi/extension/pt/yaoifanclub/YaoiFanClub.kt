package eu.kanade.tachiyomi.extension.pt.yaoifanclub

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import okhttp3.Headers

@Source
abstract class YaoiFanClub : ZeistManga() {

    override val popularMangaSelector = "#PopularPosts3 article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override val useNewChapterFeed = true
    override val chapterCategory = "Chapter"

    override val hasFilters = true
    override val hasLanguageFilter = false
    override val hasGenreFilter = true
    override val hasStatusFilter = true

    override fun Headers.Builder.configureHeaders() = apply {
        set("Referer", "https://www.blogger.com/blogin.g?blogspotURL=$baseUrl/&type=blog&bpli=1")
    }

    override fun getGenreList() = listOf(
        "ABO", "Ação", "Anjo", "Apocalipse",
        "Aventura", "Comédia", "Drama", "Demência",
        "Demônio", "Espaço", "Esporte", "Fantasma",
        "Fantasia", "Ficção", "Game", "Gore",
        "Harem", "Histórico", "Horror", "Magia",
        "Militar", "Mistério", "Música", "Omegaverso",
        "Paródia", "Poderes", "Policial", "Psicológico",
        "Robô", "Romance", "Samurai", "Sobrenatural",
        "Suspense", "Terror", "Vampiro", "Viagem no tempo",
        "Vida Cotidiana", "Zumbi",
    ).map { Genre(it, it) }

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
