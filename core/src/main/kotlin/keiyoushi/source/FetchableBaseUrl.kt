package keiyoushi.source

/** Base url preferences that take the urls from `fetchBaseUrls`. */
interface FetchableBaseUrl {
    fun update(urls: List<String>)
}
