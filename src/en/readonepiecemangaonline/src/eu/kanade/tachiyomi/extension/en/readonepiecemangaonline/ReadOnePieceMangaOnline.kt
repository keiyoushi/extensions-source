package eu.kanade.tachiyomi.extension.en.readonepiecemangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadOnePieceMangaOnline : MangaCatalog("Read One Piece Manga Online", "https://ww12.readonepiece.com", "en") {
    override val sourceList = listOf(
        Pair("One Piece", "$baseUrl/manga/one-piece/"),
        Pair("Colored", "$baseUrl/manga/one-piece-digital-colored-comics/"),
        Pair("Soma x Sanji", "$baseUrl/manga/shokugeki-no-sanji-one-shot/"),
        Pair("OP x Toriko", "$baseUrl/manga/one-piece-x-toriko/"),
        Pair("Party", "$baseUrl/manga/one-piece-party/"),
        Pair("DB x OP", "$baseUrl/manga/dragon-ball-x-one-piece/"),
        Pair("Wanted!", "$baseUrl/manga/wanted-one-piece/"),
        Pair("Ace's Story", "$baseUrl/manga/one-piece-ace-s-story/"),
        Pair("Omake", "$baseUrl/manga/one-piece-omake/"),
        Pair("Vivre Card", "$baseUrl/manga/vivre-card-databook/"),
        Pair("Pirate Recipes", "$baseUrl/manga/one-piece-pirate-recipes/"),
        Pair("Databook", "$baseUrl/manga/one-piece-databook/"),
        Pair("Ace's Story Manga", "$baseUrl/manga/one-piece-ace-story-manga/"),
        Pair("OP Academy", "$baseUrl/manga/one-piece-academy/"),
        Pair("MONSTERS", "$baseUrl/manga/monsters/"),
        Pair("Zoro Novel", "$baseUrl/manga/one-piece-novel-zoro/"),
        Pair("OP in Love", "$baseUrl/manga/one-piece-in-love/"),
        Pair("Heroines", "$baseUrl/manga/one-piece-novel-heroines/"),
    )
}
