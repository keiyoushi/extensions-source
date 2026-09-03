@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import android.webkit.WebSettings
import java.lang.reflect.InvocationHandler

internal interface WebkitToCompatConverterBoundaryInterface {
    fun convertSettings(webSettings: WebSettings?): InvocationHandler
}
