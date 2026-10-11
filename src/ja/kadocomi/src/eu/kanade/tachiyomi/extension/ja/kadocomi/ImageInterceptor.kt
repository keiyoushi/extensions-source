package eu.kanade.tachiyomi.extension.ja.kadocomi

import keiyoushi.lib.xorinterceptor.FragmentXorInterceptor
import keiyoushi.utils.decodeHex

class ImageInterceptor : FragmentXorInterceptor({ it.decodeHex() })
