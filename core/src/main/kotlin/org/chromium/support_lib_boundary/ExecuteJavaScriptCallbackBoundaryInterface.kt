@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

internal interface ExecuteJavaScriptCallbackBoundaryInterface {
    fun onSuccess(result: String)
    fun onFailure(errorType: Int, errorDetails: String)
}
