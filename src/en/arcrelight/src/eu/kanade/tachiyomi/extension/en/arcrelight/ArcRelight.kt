package eu.kanade.tachiyomi.extension.en.arcrelight
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangadventure.MangAdventure

/** Arc-Relight source. */
class ArcRelight : MangAdventure("Arc-Relight", "https://arc-relight.com") {
    override val categories = listOf(
        "4-Koma",
        "Chaos;Head",
        "Collection",
        "Comedy",
        "Drama",
        "Jubilee",
        "Mystery",
        "Psychological",
        "Robotics;Notes",
        "Romance",
        "Sci-Fi",
        "Seinen",
        "Shounen",
        "Steins;Gate",
        "Supernatural",
        "Tragedy",
    )
}
