package eu.kanade.tachiyomi.extension.en.eightmuses

import eu.kanade.tachiyomi.multisrc.eromuse.EroMuse
import keiyoushi.annotation.Source

@Source
class EightMuses(
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
    override val id: Long,
) : EroMuse()
