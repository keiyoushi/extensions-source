package eu.kanade.tachiyomi.extension.ja.zebrack

import keiyoushi.network.FragmentXorInterceptor
import keiyoushi.utils.decodeHex

class ImageInterceptor :
    FragmentXorInterceptor({
        if (it.contains("key=")) it.substringAfter("key=").decodeHex() else null
    })
