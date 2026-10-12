package eu.kanade.tachiyomi.extension.ja.mangaparkpublisher

import android.util.Base64
import keiyoushi.network.FragmentXorInterceptor

class ImageInterceptor : FragmentXorInterceptor({ Base64.decode(it, Base64.DEFAULT) })
