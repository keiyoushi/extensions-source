package eu.kanade.tachiyomi.extension.ja.mangamee

import keiyoushi.network.FragmentXorInterceptor
import keiyoushi.utils.decodeHex

class ImageInterceptor :
    FragmentXorInterceptor({
        if (it.contains("key=")) it.substringAfter("key=").decodeHex() else null
    })
