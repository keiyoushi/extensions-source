package eu.kanade.tachiyomi.extension.en.readbluelockmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadBlueLockMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        Pair("Blue Lock", "$baseUrl/manga/blue-lock/"),
        Pair("Blue Lock: Episode Nagi", "$baseUrl/manga/blue-lock-episode-nagi/"),
        Pair("Another Blue Lock: Episode Omotesando", "$baseUrl/manga/another-blue-lock-episode-omotesando/"),
        Pair("Blue Lock Character Book EGOIST BIBLE", "$baseUrl/manga/blue-lock-character-book-egoist-bible/"),
        Pair("Blue Lock Light Novel", "$baseUrl/manga/blue-lock-light-novel/"),
        Pair("Super Ball Girls", "$baseUrl/manga/super-ball-girls/"),
        Pair("Jagaaaaaan", "$baseUrl/manga/jagaaaaaan/"),
        Pair("Grashros", "$baseUrl/manga/grashros/"),
        Pair("We Did It", "$baseUrl/manga/bokutachi-ga-yarimashita/"),
        Pair("Billion Dogs", "$baseUrl/manga/billion-dogs/"),
        Pair("Like God Says", "$baseUrl/manga/kamisama-no-iutoori/"),
        Pair("As The Gods Will - The Second Series", "$baseUrl/manga/kamisama-no-iutoori-ii/"),
        Pair("As The Gods Will Zero", "$baseUrl/manga/kamisama-no-iutoori-0/"),
        Pair("Dolly Kill Kill", "$baseUrl/manga/dolly-kill-kill/"),
        Pair("Tesla Note", "$baseUrl/manga/tesla-note/"),
        Pair("Invisible Joe", "$baseUrl/manga/invisible-joe/"),
        Pair("Embers", "$baseUrl/manga/embers/"),
    )
}
