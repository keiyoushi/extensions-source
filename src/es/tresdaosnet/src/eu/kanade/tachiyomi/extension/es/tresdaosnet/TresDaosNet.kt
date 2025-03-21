package eu.kanade.tachiyomi.extension.es.tresdaosnet

import eu.kanade.tachiyomi.multisrc.madara.Madara

class TresDaosNet : Madara(
    "Tres Daos Net",
    "https://tresdaos.net",
    "es",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
