package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import keiyoushi.annotation.Source
import okhttp3.Headers

@Source
abstract class Hipercool : Hiper() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .set("x-flux-node", "G2ZsDdWhUwdU82Vw")
}
