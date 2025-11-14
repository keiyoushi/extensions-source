package eu.kanade.tachiyomi.extension.pt.aurorascan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class AuroraScan : GreenShit(
    "Aurora Scan",
    "https://www.serenitytoons.win/",
    "pt-BR",
    scanId = 4,
) {
    override val targetAudience = TargetAudience.Shoujo
}
