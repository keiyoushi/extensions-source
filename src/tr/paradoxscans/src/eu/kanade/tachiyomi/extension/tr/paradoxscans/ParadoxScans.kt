package eu.kanade.tachiyomi.extension.tr.paradoxscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source
import java.time.ZoneId
import java.time.ZoneOffset

@Source
abstract class ParadoxScans : InitManga() {

    override val popularUrlSlug = "manga-ranking"

    override val latestUrlSlug = "recently-updated"

    override val siteZone: ZoneId = ZoneOffset.UTC
}
