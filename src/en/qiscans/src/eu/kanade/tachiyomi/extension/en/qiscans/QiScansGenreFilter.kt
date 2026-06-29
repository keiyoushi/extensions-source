package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.source.model.Filter

class QiScansGenreFilter : Filter.Select<String>("Genre", DISPLAY) {
    val value get() = VALUES[state]

    companion object {
        private val ENTRIES = arrayOf(
            "All" to "",
            "Acting" to "acting",
            "Action" to "action",
            "Action (Alt)" to "action-582",
            "Adventure" to "adventure",
            "Adventure (Alt)" to "adventure-589",
            "Apocalypse" to "apocalypce",
            "Comedy" to "comedy",
            "Cooking" to "cooking",
            "Crazy MC" to "crazy-mc",
            "Cultivation" to "cultivation",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Fantasy (Alt)" to "fantasy-747",
            "Fight" to "fight",
            "Gender Bender" to "gender-bender",
            "Harem" to "harem",
            "Hidden" to "hidden",
            "Historical" to "historical",
            "Horror" to "horror",
            "Josei" to "josei",
            "Live" to "live",
            "Magic" to "magic",
            "Manhua" to "manhua",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Mecha" to "mecha",
            "Medieval Area" to "medieval-area",
            "Munchkin" to "munchkin",
            "Murim" to "murim",
            "Mystery" to "mystery",
            "Myth" to "myth",
            "Politics" to "politics",
            "Psychological" to "psychological",
            "Reincarnation" to "reincarnation",
            "Revenge" to "revenge",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Sci-Fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Sports" to "sports",
            "Supernatural" to "supernatural",
            "Superpower" to "superpower",
            "System" to "system",
            "Taming" to "taming",
            "Tower" to "tower",
            "Tragedy" to "tragedy",
            "Urban" to "urban",
            "Vampires" to "vampiers",
            "Virtual Reality" to "virtual-reality",
            "Wuxia" to "wuxia",
        )
        val DISPLAY = ENTRIES.map { it.first }.toTypedArray()
        val VALUES = ENTRIES.map { it.second }.toTypedArray()
    }
}
