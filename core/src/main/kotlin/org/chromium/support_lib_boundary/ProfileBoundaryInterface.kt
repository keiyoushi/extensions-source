@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

internal interface ProfileBoundaryInterface {
    fun addOriginMatchedHeader(headerName: String, headerValue: String, originRules: Set<String>)
    fun clearOriginMatchedHeader(headerName: String, headerValue: String)
}
