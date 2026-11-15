package eu.kanade.tachiyomi.extension.pt.verdinha

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import keiyoushi.annotation.Source

@Source
abstract class Verdinha : GreenShit() {
    override val apiUrl = "https://api.verdinha.wtf"
    override val cdnUrl = "https://cdn.verdinha.wtf"
    override val cdnApiUrl = "https://api.verdinha.wtf/cdn"
    override val scanId = "1"
}
