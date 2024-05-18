package eu.kanade.tachiyomi.extension.en.msyfanyi

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MSYFanyi : Madara(
    "MSY Fanyi",
    "https://msyfanyi.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaSubString = "msy"
}
