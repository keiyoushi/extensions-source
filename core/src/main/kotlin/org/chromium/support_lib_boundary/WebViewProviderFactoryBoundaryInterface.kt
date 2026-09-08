@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import java.lang.reflect.InvocationHandler

internal interface WebViewProviderFactoryBoundaryInterface {
    val webkitToCompatConverter: InvocationHandler
    val supportedFeatures: Array<String>
}
