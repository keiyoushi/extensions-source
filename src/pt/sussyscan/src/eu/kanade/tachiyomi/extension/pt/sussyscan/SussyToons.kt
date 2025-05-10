package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit

class SussyToons : GreenShit(
    "Sussy Toons",
    "https://www.sussytoons.wtf",
    "pt-BR",
) {
    override val id = 6963507464339951166

    override val versionId = 2

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int) = fetchLatestUpdates(page)
}
