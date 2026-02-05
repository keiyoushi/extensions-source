package eu.kanade.tachiyomi.extension.en.writerscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class WriterScans :
    Keyoapp(
        "Writer Scans",
        "https://writerscans.com",
        "en",
    ) {
    override fun popularMangaSelector() = "div:contains(Trending) + div .group.overflow-hidden"
}
