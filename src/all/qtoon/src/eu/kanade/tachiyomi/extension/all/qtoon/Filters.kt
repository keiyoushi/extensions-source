package eu.kanade.tachiyomi.extension.all.qtoon

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class TagFilter :
    SelectFilter(
        name = "Tags",
        options = listOf(
            "All" to "-1",
            "Action" to "4",
            "Adaptation" to "22",
            "Adult" to "21",
            "Adventure" to "38",
            "Age Gap" to "41",
            "BL" to "5",
            "Bloody" to "53",
            "Cheating/Infidelity" to "44",
            "Childhood Friends" to "42",
            "College life" to "29",
            "Comedy" to "3",
            "Crime" to "48",
            "Doujinshi" to "43",
            "Drama" to "6",
            "Fantasy" to "2",
            "GL" to "17",
            "Harem" to "31",
            "Hentai" to "34",
            "Historical" to "16",
            "Horror" to "12",
            "Isekai" to "25",
            "Josei(W)" to "23",
            "Magic" to "28",
            "Manga" to "26",
            "Manhwa" to "19",
            "Mature" to "18",
            "Mystery" to "14",
            "Office Workers" to "33",
            "Omegaverse" to "35",
            "Oneshot" to "50",
            "Psychological" to "32",
            "Reincarnation" to "30",
            "Revenge" to "45",
            "Reverse Harem" to "52",
            "Romance" to "1",
            "Royalty" to "49",
            "School Life" to "27",
            "Sci-fi" to "9",
            "Seinen(M)" to "24",
            "Shoujo" to "55",
            "Shounen ai" to "36",
            "Shounen(B)" to "40",
            "Slice of life" to "10",
            "Smut" to "20",
            "Sports" to "15",
            "Superhero" to "13",
            "Supernatural" to "8",
            "Thriller" to "7",
            "Time Travel" to "39",
            "Tragedy" to "56",
            "Transmigration" to "51",
            "Vampires" to "54",
            "Villainess" to "46",
            "Violence" to "37",
            "Yakuzas" to "47",
        ),
    )

class GenderFilter :
    SelectFilter(
        name = "Gender",
        options = listOf(
            "All" to "-1",
            "Male" to "101",
            "Female" to "103",
        ),
    )

class StatusFilter :
    SelectFilter(
        name = "Status",
        options = listOf(
            "All" to "-1",
            "Ongoing" to "101",
            "Completed" to "103",
        ),
    )

class SortFilter :
    SelectFilter(
        name = "Sort",
        options = listOf(
            "Hot" to "hot",
            "New" to "new",
            "Rate" to "rate",
        ),
    )

class HomePageFilter :
    SelectFilter(
        name = "Home Page Section",
        options = listOf(
            "" to "",
            "✨ Trending Updates ✨" to "as_l9zC15glGlkcS7yIamHQ",
            "🥵 Hottest BL" to "as_8CgkZpYmgOr0aAYHsePs",
            "❤️‍🔥 Hot & Sweet Desire ❤️‍🔥" to "as_DP6QM8o_pgvu4Q8uVNjt",
            "🔄 Rebirth. Revenge. Reclaim. 💥" to "as_16RPgJOVcNQ11N97pOe4B3",
            "🇯🇵 Manga Paradise ⛩️" to "as_eF_lw9vKVUWpf0trKDk1",
            "🏫 Campus Love, Teen Feels 💓" to "as_RtRk4KegzUjsoEEUGWOK",
            "📖 Reborn in a Novel/Game 🎮" to "as_16IPE5so_KZ13zYzBRSf4O",
            "⚔️ Level Up to a Top Hunter!" to "as_fQnbLm2ZSymVTHEWoxMf",
            "✍️ Must-Read Completed" to "as_fdZX3BgTPGRELzqlfg_A",
            "🌸 BL Vibes, Innocent Hearts 💝" to "as_FPRnQVKG6qJ5poOo7FKE",
            "🌅 Reborn! A New Life Awaits 🔥" to "as_eth_Jc0XcLftyVnVJOnb",
            "💕 Beyond Friendship 💕 LGBT+" to "as_JW0c05O4zWPFSmDW0iCH",
        ),
    )
