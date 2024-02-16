package eu.kanade.tachiyomi.extension.ja.rawmangas

import eu.kanade.tachiyomi.multisrc.madara.Madara

class RawMangas : Madara("RawMangas", "https://raw-mangas.com", "ja") {
    override val mangaSubString = "comic"
}
