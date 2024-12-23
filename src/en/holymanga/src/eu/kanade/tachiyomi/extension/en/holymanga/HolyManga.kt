package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.multisrc.zbulu.Zbulu

class HolyManga : Zbulu(
    "HolyManga",
    "https://w34.holymanga.net",
    "en",
) {
    override val supportsLatest = false
}
