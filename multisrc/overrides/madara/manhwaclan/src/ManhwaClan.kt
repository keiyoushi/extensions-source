package eu.kanade.tachiyomi.extension.en.manhwaclan

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaClan : Madara("ManhwaClan", "https://manhwaclan.com", "en") {
    // sfw content marked as nsfw (no nsfw content on the site yet)
    override val adultContentFilterOptions: Array<String> = arrayOf("All", "Only", "None")
}
