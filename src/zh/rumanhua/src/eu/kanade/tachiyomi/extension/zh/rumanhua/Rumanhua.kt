package eu.kanade.tachiyomi.extension.zh.rumanhua

import eu.kanade.tachiyomi.multisrc.mmlook.MMLook
import eu.kanade.tachiyomi.source.model.SManga

class Rumanhua : MMLook("如漫画", "https://m.rumanhua1.com", "https://www.rumanhua1.com") {
    override fun SManga.formatUrl() = apply { url = "/$url/" }
}
