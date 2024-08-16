package eu.kanade.tachiyomi.extension.all.eromanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Eromanhwa : Madara(
    "Eromanhwa",
    "https://eromanhwa.org",
    "all",
) {
    override val id = 3597355706480775153 // accidently set lang to en...
    override val useNewChapterEndpoint = true
}
