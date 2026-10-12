package eu.kanade.tachiyomi.extension.ja.mangaboxme

import keiyoushi.network.FragmentXorInterceptor

class ImageInterceptor : FragmentXorInterceptor({ byteArrayOf(it.toByte()) })
