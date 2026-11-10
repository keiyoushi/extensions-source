package eu.kanade.tachiyomi.extension.en.kotokotek

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class ViewerModel(val siteFeaturesConfigs: SiteFeaturesConfigs)

@Serializable
class SiteFeaturesConfigs(val dynamicPages: DynamicPages)

@Serializable
class DynamicPages(val prefixToRouterFetchData: Map<String, RouterFetchData>)

@Serializable
class RouterFetchData(val urlData: UrlData, val optionsData: OptionsData)

@Serializable
class UrlData(val basePath: String, val queryParams: String, val appDefinitionId: String)

@Serializable
class OptionsData(val bodyData: RouterBodyData)

@Serializable
class RouterBodyData(
    val pageRoles: JsonElement,
    val routerPrefix: String,
    val config: JsonElement,
    val roleVariations: JsonElement,
)

@Serializable
class RouterRequest(val urlParams: Map<String, String>, val body: RouterRequestBody)

@Serializable
class RouterRequestBody(
    val pageRoles: JsonElement,
    val routerPrefix: String,
    val config: JsonElement,
    val roleVariations: JsonElement,
    val requestInfo: RequestInfo,
    val routerSuffix: String,
    val fullUrl: String,
)

@Serializable
class RequestInfo(val env: String, val formFactor: String)

@Serializable
class RouterResponse(val result: RouterResult)

@Serializable
class RouterResult(val data: RouterData)

@Serializable
class RouterData(val items: List<RouterItem>)

@Serializable
class RouterItem(val mediagallery: List<GalleryMedia>)

@Serializable
class GalleryMedia(val src: String)

@Serializable
class AccessTokens(val apps: Map<String, AppToken>)

@Serializable
class AppToken(val instance: String)
