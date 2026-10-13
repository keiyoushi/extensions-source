@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import android.net.Uri
import android.webkit.WebView
import java.lang.reflect.InvocationHandler

internal interface WebMessageListenerBoundaryInterface : FeatureFlagHolderBoundaryInterface {
    fun onPostMessage(
        view: WebView,
        message: InvocationHandler,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: InvocationHandler,
    )
}
