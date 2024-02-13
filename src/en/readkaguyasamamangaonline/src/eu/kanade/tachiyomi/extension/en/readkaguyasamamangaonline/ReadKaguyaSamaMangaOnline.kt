package eu.kanade.tachiyomi.extension.en.readkaguyasamamangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadKaguyaSamaMangaOnline : MangaCatalog("Read Kaguya-sama Manga Online", "https://ww1.readkaguyasama.com", "en") {
    override val sourceList = listOf(
        Pair("Kaguya-sama: Love is War", "$baseUrl/manga/kaguya-sama-love-is-war/"),
        Pair("Official Doujin", "$baseUrl/manga/kaguya-wants-to-be-confessed-to-official-doujin/"),
        Pair("Spin off", "$baseUrl/manga/we-want-to-talk-about-kaguya/"),
        Pair("Light Novel", "$baseUrl/manga/kaguya-sama-light-novel/"),
        Pair("Instant Bullet", "$baseUrl/manga/ib-instant-bullet/"),
        Pair("Oshi no Ko", "$baseUrl/manga/oshi-no-ko/"),
        Pair("Sayonara Piano Sonata", "$baseUrl/manga/sayonara-piano-sonata/"),
        Pair("Original Hinatazaka", "$baseUrl/manga/original-hinatazaka/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
