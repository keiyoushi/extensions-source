package eu.kanade.tachiyomi.extension.en.cathexis

import kotlinx.serialization.Serializable

@Serializable
class ViewerModel(val siteFeaturesConfigs: SiteFeaturesConfigs)

@Serializable
class SiteFeaturesConfigs(val router: Router)

@Serializable
class Router(val pagesMap: Map<String, WixPage>)

@Serializable
class WixPage(val title: String, val pageUriSEO: String)
