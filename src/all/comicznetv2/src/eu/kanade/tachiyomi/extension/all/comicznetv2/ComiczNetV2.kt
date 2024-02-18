package eu.kanade.tachiyomi.extension.all.comicznetv2

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ComiczNetV2 : Madara("Comicz.net v2", "https://v2.comiz.net", "all") {
    override val useNewChapterEndpoint = false
}
