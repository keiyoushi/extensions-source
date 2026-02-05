package eu.kanade.tachiyomi.extension.pt.mangalivreblog

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    GenreFilter(),
    StatusFilter(),
    RatingMinFilter(),
    SortFilter(),
    OrderFilter(),
)

interface UrlQueryFilter {
    fun selectedValue(): String
}

class GenreFilter :
    Filter.Select<String>("Gênero", genres.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = genres[state].second
}

class StatusFilter :
    Filter.Select<String>("Status", statuses.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = statuses[state].second
}

class RatingMinFilter :
    Filter.Select<String>("Avaliação Mínima", ratingMins.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = ratingMins[state].second
}

class SortFilter :
    Filter.Select<String>("Ordenar por", sorts.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = sorts[state].second
}

class OrderFilter :
    Filter.Select<String>("Ordem", orders.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = orders[state].second
}

private val genres = listOf(
    Pair("Todos os gêneros", ""),
    Pair("4-Koma", "4-koma"),
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adventure", "adventure"),
    Pair("Aliens", "aliens"),
    Pair("Animals", "animals"),
    Pair("Award Winning", "award-winning"),
    Pair("Boys' Love", "boys-love"),
    Pair("Comedy", "comedy"),
    Pair("Cooking", "cooking"),
    Pair("Crime", "crime"),
    Pair("Crossdressing", "crossdressing"),
    Pair("Delinquents", "delinquents"),
    Pair("Demons", "demons"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Erotica", "erotica"),
    Pair("Fantasy", "fantasy"),
    Pair("Full Color", "full-color"),
    Pair("Genderswap", "genderswap"),
    Pair("Ghosts", "ghosts"),
    Pair("Girls' Love", "girls-love"),
    Pair("Gore", "gore"),
    Pair("Gyaru", "gyaru"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Incest", "incest"),
    Pair("Isekai", "isekai"),
    Pair("Loli", "loli"),
    Pair("Long Strip", "long-strip"),
    Pair("Mafia", "mafia"),
    Pair("Magic", "magic"),
    Pair("Magical Girls", "magical-girls"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Military", "military"),
    Pair("Monster Girls", "monster-girls"),
    Pair("Monsters", "monsters"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Ninja", "ninja"),
    Pair("Office Workers", "office-workers"),
    Pair("Official Colored", "official-colored"),
    Pair("Philosophical", "philosophical"),
    Pair("Police", "police"),
    Pair("Post-Apocalyptic", "post-apocalyptic"),
    Pair("Psychological", "psychological"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Romance", "romance"),
    Pair("Samurai", "samurai"),
    Pair("School Life", "school-life"),
    Pair("Sci-Fi", "sci-fi"),
    Pair("Self-Published", "self-published"),
    Pair("Sexual Violence", "sexual-violence"),
    Pair("Shota", "shota"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Sports", "sports"),
    Pair("Superhero", "superhero"),
    Pair("Supernatural", "supernatural"),
    Pair("Survival", "survival"),
    Pair("Thriller", "thriller"),
    Pair("Time Travel", "time-travel"),
    Pair("Traditional Games", "traditional-games"),
    Pair("Tragedy", "tragedy"),
    Pair("Vampires", "vampires"),
    Pair("Video Games", "video-games"),
    Pair("Villainess", "villainess"),
    Pair("Virtual Reality", "virtual-reality"),
    Pair("Web Comic", "web-comic"),
    Pair("Wuxia", "wuxia"),
    Pair("Zombies", "zombies"),
)

private val statuses = listOf(
    Pair("Todos os status", ""),
    Pair("Cancelado", "cancelado"),
    Pair("Completo", "completo"),
    Pair("Em Andamento", "em-andamento"),
    Pair("Em Lançamento", "em-lancamento"),
    Pair("Hiato", "hiato"),
)

private val ratingMins = listOf(
    Pair("Qualquer avaliação", "0"),
    Pair("1 ou mais", "1"),
    Pair("2 ou mais", "2"),
    Pair("3 ou mais", "3"),
    Pair("4 ou mais", "4"),
    Pair("5 ou mais", "5"),
    Pair("6 ou mais", "6"),
    Pair("7 ou mais", "7"),
    Pair("8 ou mais", "8"),
    Pair("9 ou mais", "9"),
    Pair("10 ou mais", "10"),
)

private val sorts = listOf(
    Pair("Título", "title"),
    Pair("Data de adição", "date"),
    Pair("Avaliação", "rating"),
)

private val orders = listOf(
    Pair("Crescente", "asc"),
    Pair("Decrescente", "desc"),
)
