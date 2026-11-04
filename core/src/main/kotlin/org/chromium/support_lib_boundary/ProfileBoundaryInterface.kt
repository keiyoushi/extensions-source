@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

internal interface ProfileBoundaryInterface {
    fun setOriginMatchedHeader(headerName: String, headerValue: String, originRules: Set<String>)
}
