package eu.kanade.tachiyomi.extension.zh.rumanhua

import eu.kanade.tachiyomi.multisrc.mmlook.MMLook
import keiyoushi.annotation.Source

@Source
abstract class Rumanhua : MMLook() {

    override val useLegacyMangaUrl = true
}
