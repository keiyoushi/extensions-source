package eu.kanade.tachiyomi.extension.en.readbluelockmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadBlueLockMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        "Blue Lock" to "$baseUrl/manga/blue-lock/",
        "Blue Lock: Episode Nagi" to "$baseUrl/manga/blue-lock-episode-nagi/",
        "Another Blue Lock: Episode Omotesando" to "$baseUrl/manga/another-blue-lock-episode-omotesando/",
        "Another Blue Lock: Episode Harajuku" to "$baseUrl/manga/another-blue-lock-episode-harajuku/",
        "Blue Lock Full Color Selection" to "$baseUrl/manga/blue-lock-full-color-selection/",
        "Blue Lock Character Book EGOIST BIBLE" to "$baseUrl/manga/blue-lock-character-book-egoist-bible/",
        "Blue Lock Light Novel" to "$baseUrl/manga/blue-lock-light-novel/",
        "Super Ball Girls" to "$baseUrl/manga/super-ball-girls/",
        "Jagaaaaaan" to "$baseUrl/manga/jagaaaaaan/",
        "Grashros" to "$baseUrl/manga/grashros/",
        "We Did It" to "$baseUrl/manga/bokutachi-ga-yarimashita/",
        "Billion Dogs" to "$baseUrl/manga/billion-dogs/",
        "Like God Says" to "$baseUrl/manga/kamisama-no-iutoori/",
        "As The Gods Will - The Second Series" to "$baseUrl/manga/kamisama-no-iutoori-ii/",
        "As The Gods Will Zero" to "$baseUrl/manga/kamisama-no-iutoori-0/",
        "Dolly Kill Kill" to "$baseUrl/manga/dolly-kill-kill/",
        "Tesla Note" to "$baseUrl/manga/tesla-note/",
        "Invisible Joe" to "$baseUrl/manga/invisible-joe/",
        "Embers" to "$baseUrl/manga/embers/",
        "Aoku Somero" to "$baseUrl/manga/aoku-somero/",
    )
}
