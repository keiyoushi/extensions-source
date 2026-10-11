package eu.kanade.tachiyomi.extension.ja.zebrack

import keiyoushi.lib.xorinterceptor.FragmentXorInterceptor
import keiyoushi.utils.decodeHex

class ImageInterceptor :
    FragmentXorInterceptor({
        if (it.contains("key=")) it.substringAfter("key=").decodeHex() else null
    })
