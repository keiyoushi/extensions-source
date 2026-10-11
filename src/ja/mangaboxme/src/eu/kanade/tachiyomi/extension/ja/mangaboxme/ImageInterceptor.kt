package eu.kanade.tachiyomi.extension.ja.mangaboxme

import keiyoushi.lib.xorinterceptor.FragmentXorInterceptor

class ImageInterceptor : FragmentXorInterceptor({ byteArrayOf(it.toByte()) })
