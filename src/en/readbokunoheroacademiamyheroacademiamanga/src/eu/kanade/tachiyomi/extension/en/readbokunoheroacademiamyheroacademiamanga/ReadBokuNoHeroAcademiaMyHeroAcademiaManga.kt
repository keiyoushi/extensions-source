package eu.kanade.tachiyomi.extension.en.readbokunoheroacademiamyheroacademiamanga

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadBokuNoHeroAcademiaMyHeroAcademiaManga : MangaCatalog("Read Boku no Hero Academia My Hero Academia Manga", "https://ww6.readmha.com", "en") {
    override val sourceList = listOf(
        Pair("Boku no Hero Academia", "$baseUrl/manga/boku-no-hero-academia/"),
        Pair("Vigilante", "$baseUrl/manga/vigilante-boku-no-hero-academia-illegals/"),
        Pair("Team Up", "$baseUrl/manga/my-hero-academia-team-up-mission/"),
        Pair("MHA Smash", "$baseUrl/manga/boku-no-hero-academia-smash/"),
        Pair("MHA: School Brief", "$baseUrl/manga/my-hero-academia-school-briefs/"),
        Pair("Rising", "$baseUrl/manga/deku-bakugo-rising/"),
        Pair("Colored", "$baseUrl/manga/boku-no-hero-academia-colored/"),
        Pair("Oumagadoki Zoo", "$baseUrl/manga/oumagadoki-zoo/"),
        Pair("Sensei no Bulge", "$baseUrl/manga/sensei-no-bulge/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
