package eu.kanade.tachiyomi.extension.en.timelesstoons

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import keiyoushi.annotation.Source

@Source
abstract class TimelessToons : Keyoapp() {

    override fun popularMangaSelector() = "div:has(> h2:contains(Trending)) + div .group"

    override fun latestUpdatesSelector() = "div.grid > div.group.latest-poster"
}
