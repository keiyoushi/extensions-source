package eu.kanade.tachiyomi.extension.id.kaguya

import eu.kanade.tachiyomi.multisrc.aesir.Aesir
import keiyoushi.annotation.Source

@Source
abstract class Kaguya : Aesir() {

    override val apiUrl = "https://tsuki.kaguya.re/api/v1"

    override val cdnUrl = "https://storage.kaguyacdn.my.id"
}
