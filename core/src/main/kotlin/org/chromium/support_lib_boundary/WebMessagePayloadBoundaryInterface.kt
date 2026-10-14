@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

internal interface WebMessagePayloadBoundaryInterface {
    fun getType(): Int
    fun getAsString(): String?
    fun getAsArrayBuffer(): ByteArray
}
