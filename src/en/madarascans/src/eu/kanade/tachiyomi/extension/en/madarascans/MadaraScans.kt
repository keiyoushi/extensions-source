package eu.kanade.tachiyomi.extension.en.madarascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class MadaraScans : MangaThemesia(
    "Madara Scans",
    "https://madarascans.com",
    "en",
    mangaUrlDirectory = "/series",
) {
    override fun chapterListSelector() = "li[data-num]:has(> a[href]:not([data-bs-target='#lockedChapterModal']))"
}
