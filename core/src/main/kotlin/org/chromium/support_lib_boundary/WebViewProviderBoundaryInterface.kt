@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import java.lang.reflect.InvocationHandler

internal interface WebViewProviderBoundaryInterface {
    fun setAudioMuted(mute: Boolean)
    fun addDocumentStartJavaScript(script: String, allowedOriginRules: Array<String>): InvocationHandler
    fun getJavaScriptWorld(worldName: String): Int
    fun addJavaScriptOnEvent(script: String, allowedOriginRules: Array<String>, event: Int, worldName: String): InvocationHandler
    fun addWebMessageListener(jsObjectName: String, allowedOriginRules: Array<String>, listener: InvocationHandler)
}
