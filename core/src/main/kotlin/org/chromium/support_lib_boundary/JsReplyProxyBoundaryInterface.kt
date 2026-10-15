@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import java.lang.reflect.InvocationHandler

internal interface JsReplyProxyBoundaryInterface {
    fun postMessage(message: String)
    fun executeJavaScript(script: String, callback: InvocationHandler)
}
