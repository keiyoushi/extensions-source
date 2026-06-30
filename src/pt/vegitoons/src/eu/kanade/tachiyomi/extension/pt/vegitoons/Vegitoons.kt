package eu.kanade.tachiyomi.extension.pt.vegitoons

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import keiyoushi.annotation.Source

@Source
abstract class Vegitoons : GreenShit() {
    override val apiUrl = "https://api.vegitoons.black"
    override val cdnUrl = "https://cdn.verdinha.wtf"
    override val cdnApiUrl = "https://api.vegitoons.black/cdn"
    override val scanId = "1"
}
