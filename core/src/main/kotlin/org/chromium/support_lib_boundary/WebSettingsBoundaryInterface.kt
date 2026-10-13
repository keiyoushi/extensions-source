@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

internal interface WebSettingsBoundaryInterface {
    fun setUserAgentMetadataFromMap(uaMetadata: MutableMap<String, Any>)
    val userAgentMetadataMap: MutableMap<String, Any>
    fun setWillSuppressErrorPage(suppress: Boolean)
    fun getWillSuppressErrorPage(): Boolean
    fun setIncludeCookiesOnIntercept(include: Boolean)
    fun getIncludeCookiesOnIntercept(): Boolean
}
