package eu.kanade.tachiyomi.extension.en.mangaowlblog

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOwlBlog : Madara("MangaOwl.blog (unoriginal)", "https://mangaowl.blog", "en") {
    override val useNewChapterEndpoint = false
}
