package eu.kanade.tachiyomi.extension.en.arcrelight

import eu.kanade.tachiyomi.multisrc.mangadventure.MangAdventure
import keiyoushi.annotation.Source

/** Arc-Relight source. */
@Source
abstract class ArcRelight : MangAdventure() {
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
