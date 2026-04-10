package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.source.model.Filter

class QiScansGenreFilter : Filter.Select<String>("Genre", DISPLAY) {
    val value get() = VALUES[state]

    companion object {
        private val ENTRIES = arrayOf(
            "All" to "",
            "Adventure" to "adventure",
            "Adventure (Alt)" to "adventure-589",
            "Live" to "live",
            "Acting" to "acting",
            "Action" to "action",
            "Action (Alt)" to "action-582",
            "Comedy" to "comedy",
            "Cooking" to "cooking",
            "Crazy MC" to "crazy-mc",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Fantasy (Alt)" to "fantasy-747",
            "Fight" to "fight",
            "Gender Bender" to "gender-bender",
            "Harem" to "harem",
            "Historical" to "historical",
            "Horror" to "horror",
            "Josei" to "josei",
            "Manhua" to "manhua",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Mecha" to "mecha",
            "Murim" to "murim",
            "Mystery" to "mystery",
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
            "Tragedy" to "tragedy",
            "Virtual Reality" to "virtual-reality",
            "Apocalypse" to "apocalypce",
            "Cultivation" to "cultivation",
            "Hidden" to "hidden",
            "Magic" to "magic",
            "Medieval Area" to "medieval-area",
            "Munchkin" to "munchkin",
            "Myth" to "myth",
            "Politics" to "politics",
            "Superpower" to "superpower",
            "System" to "system",
            "Taming" to "taming",
            "Tower" to "tower",
            "Urban" to "urban",
            "Vampires" to "vampiers",
            "Wuxia" to "wuxia",
        )
        val DISPLAY = ENTRIES.map { it.first }.toTypedArray()
        val VALUES = ENTRIES.map { it.second }.toTypedArray()
    }
}
