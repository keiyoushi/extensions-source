@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import java.util.concurrent.Executor

internal interface ProxyControllerBoundaryInterface {
    fun setProxyOverride(
        proxyRules: Array<Array<String>>,
        bypassRules: Array<String>,
        listener: Runnable,
        executor: Executor,
    )
    fun setProxyOverride(
        proxyRules: Array<Array<String>>,
        bypassRules: Array<String>,
        listener: Runnable,
        executor: Executor,
        reverseBypass: Boolean,
    )
    fun clearProxyOverride(listener: Runnable, executor: Executor)
}
