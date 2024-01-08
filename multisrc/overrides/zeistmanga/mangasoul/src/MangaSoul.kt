package eu.kanade.tachiyomi.extension.ar.mangasoul

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class MangaSoul : ZeistManga("Manga Soul", "https://www.manga-soul.com", "ar") {
    override val mangaDetailsSelectorInfo = ".y6x11p, .y6x11p > b > span"
}
