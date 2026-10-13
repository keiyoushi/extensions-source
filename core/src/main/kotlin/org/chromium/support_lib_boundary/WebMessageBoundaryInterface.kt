@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import java.lang.reflect.InvocationHandler

internal interface WebMessageBoundaryInterface : FeatureFlagHolderBoundaryInterface {
    fun getData(): String?
    fun getMessagePayload(): InvocationHandler?
    fun getPorts(): Array<InvocationHandler>?
}
