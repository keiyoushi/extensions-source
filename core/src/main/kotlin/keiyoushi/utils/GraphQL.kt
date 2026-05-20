package keiyoushi.utils

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

@Serializable
private class GraphQLRequest<V>(
    private val operationName: String? = null,
    private val query: String? = null,
    private val variables: V? = null,
    private val extensions: JsonElement? = null,
)

@PublishedApi
@Serializable
internal class GraphQLResponse<T>(
    val data: T? = null,
    val errors: List<GraphQLError>? = null,
) {
    @PublishedApi
    @Serializable
    internal class GraphQLError(
        val message: String,
    )
}

@Serializable
private class PersistedQueryExtension(
    private val persistedQuery: PersistedQuery,
) {
    @Serializable
    class PersistedQuery(
        private val version: Int,
        private val sha256Hash: String,
    )
}

/**
 * Intercepts HTTP responses and throws [GraphQLException] if the body contains GraphQL errors.
 */
class GraphQLErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response
        val body = response.peekBody(Long.MAX_VALUE).string()
        val errors = runCatching { body.parseAs<GraphQLResponse<Unit>>().errors }.getOrNull()
        if (!errors.isNullOrEmpty()) throw GraphQLException(errors.joinToString("\n") { it.message })
        return response
    }
}

/**
 * Builds a GraphQL POST [RequestBody].
 *
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables pre-encoded as a [JsonElement].
 * @param extensions Optional protocol extensions.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
fun graphQLBody(
    query: String? = null,
    operationName: String? = null,
    variables: JsonElement? = null,
    extensions: JsonElement? = null,
    json: Json = jsonInstance,
): RequestBody = GraphQLRequest(
    operationName = operationName,
    query = query,
    variables = variables,
    extensions = extensions,
).toJsonRequestBody(json)

/**
 * Typed-variables overload of [graphQLBody].
 *
 * Builds a GraphQL POST [RequestBody].
 *
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables to serialize as [JsonElement] and embed in the request body.
 * @param extensions Optional protocol extensions.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
inline fun <reified V : Any> graphQLBody(
    query: String? = null,
    operationName: String? = null,
    variables: V,
    extensions: JsonElement? = null,
    json: Json = jsonInstance,
): RequestBody = graphQLBody(
    query = query,
    operationName = operationName,
    variables = variables.toJsonElement(json),
    extensions = extensions,
    json = json,
)

/**
 * Appends GraphQL parameters (`operationName`, `query`, `variables`, `extensions`) as URL
 * query parameters. Use for sources that send GraphQL over GET rather than POST.
 * Null parameters are not appended.
 *
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables pre-encoded as a [JsonElement].
 * @param extensions Optional protocol extensions.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
fun Builder.appendGraphQLParams(
    query: String? = null,
    operationName: String? = null,
    variables: JsonElement? = null,
    extensions: JsonElement? = null,
    json: Json = jsonInstance,
): Builder = apply {
    operationName?.let { addQueryParameter("operationName", it) }
    query?.let { addQueryParameter("query", it) }
    variables?.let { addQueryParameter("variables", it.toJsonString(json)) }
    extensions?.let { addQueryParameter("extensions", it.toJsonString(json)) }
}

/**
 * Typed-variables overload of [appendGraphQLParams].
 *
 * Appends GraphQL parameters (`operationName`, `query`, `variables`, `extensions`) as URL
 * query parameters. Use for sources that send GraphQL over GET rather than POST.
 * Null parameters are not appended.
 *
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables to serialize as [JsonElement] and embed in the request body.
 * @param extensions Optional protocol extensions.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
inline fun <reified V : Any> Builder.appendGraphQLParams(
    query: String? = null,
    operationName: String? = null,
    variables: V,
    extensions: JsonElement? = null,
    json: Json = jsonInstance,
): Builder = appendGraphQLParams(
    query = query,
    operationName = operationName,
    variables = variables.toJsonElement(json),
    extensions = extensions,
    json = jsonInstance,
)

/**
 * Builds a GraphQL POST [Request].
 *
 * @param url The endpoint URL.
 * @param headers The HTTP request headers.
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables pre-encoded as a [JsonElement].
 * @param extensions Optional protocol extensions.
 * @param cache The cache control strategy.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
fun graphQLPost(
    url: String,
    headers: Headers,
    query: String? = null,
    operationName: String? = null,
    variables: JsonElement? = null,
    extensions: JsonElement? = null,
    cache: CacheControl? = null,
    json: Json = jsonInstance,
): Request {
    val body = graphQLBody(query, operationName, variables, extensions, json)
    return if (cache != null) POST(url, headers, body, cache) else POST(url, headers, body)
}

/**
 * Typed-variables overload of [graphQLPost].
 *
 * Builds a GraphQL POST [Request].
 *
 * @param url The endpoint URL.
 * @param headers The HTTP request headers.
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables to serialize as [JsonElement] and embed in the request body.
 * @param extensions Optional protocol extensions.
 * @param cache The cache control strategy.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
inline fun <reified V : Any> graphQLPost(
    url: String,
    headers: Headers,
    query: String? = null,
    operationName: String? = null,
    variables: V,
    extensions: JsonElement? = null,
    cache: CacheControl? = null,
    json: Json = jsonInstance,
): Request = graphQLPost(url, headers, query, operationName, variables.toJsonElement(json), extensions, cache, json)

/**
 * Builds a GraphQL GET [Request] with parameters encoded as URL query parameters.
 *
 * @param url The endpoint URL.
 * @param headers The HTTP request headers.
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables pre-encoded as a [JsonElement].
 * @param extensions Optional protocol extensions.
 * @param cache The cache control strategy.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
fun graphQLGet(
    url: String,
    headers: Headers,
    query: String? = null,
    operationName: String? = null,
    variables: JsonElement? = null,
    extensions: JsonElement? = null,
    cache: CacheControl? = null,
    json: Json = jsonInstance,
): Request {
    val url = url.toHttpUrl().newBuilder()
        .appendGraphQLParams(query, operationName, variables, extensions, json)
        .build()
    return if (cache != null) GET(url, headers, cache) else GET(url, headers)
}

/**
 * Typed-variables overload of [graphQLGet].
 *
 * Builds a GraphQL GET [Request] with parameters encoded as URL query parameters.
 *
 * @param url The endpoint URL.
 * @param headers The HTTP request headers.
 * @param query The GraphQL query string.
 * @param operationName The GraphQL operation name.
 * @param variables Variables to serialize as [JsonElement] and embed in the request body.
 * @param extensions Optional protocol extensions.
 * @param cache The cache control strategy.
 * @param json [Json] instance for serialization. Defaults to [jsonInstance].
 * @see persistedQueryExtension
 */
inline fun <reified V : Any> graphQLGet(
    url: String,
    headers: Headers,
    query: String? = null,
    operationName: String? = null,
    variables: V,
    extensions: JsonElement? = null,
    cache: CacheControl? = null,
    json: Json = jsonInstance,
): Request = graphQLGet(url, headers, query, operationName, variables.toJsonElement(json), extensions, cache, json)

/**
 * Pass the result to the `extensions` parameter of [graphQLBody], [graphQLPost] or [graphQLGet].
 *
 * @param hash SHA-256 hash of the query string.
 * @param version APQ protocol version. Defaults to `1`.
 */
fun persistedQueryExtension(hash: String, version: Int = 1): JsonElement = PersistedQueryExtension(PersistedQueryExtension.PersistedQuery(version, hash)).toJsonElement()

/**
 * Parses a GraphQL HTTP [Response] into [T], unwrapping `"data"` and throwing
 * [GraphQLException] if `"errors"` is non-empty.
 *
 * @throws GraphQLException If the response contains a non-empty `"errors"` array.
 * @throws IllegalStateException If `"data"` is absent or null.
 */
inline fun <reified T> Response.parseGraphQLAs(json: Json = jsonInstance): T {
    val envelope = parseAs<GraphQLResponse<T>>(json)
    val errors = envelope.errors
    if (!errors.isNullOrEmpty()) throw GraphQLException(errors.joinToString("\n") { it.message })
    return envelope.data ?: throw IllegalStateException("GraphQL response is missing the 'data' field")
}

/**
 * Parses a raw GraphQL JSON [String] into [T], unwrapping `"data"` and throwing
 * [GraphQLException] if `"errors"` is non-empty.
 *
 * @throws GraphQLException If the JSON contains a non-empty `"errors"` array.
 * @throws IllegalStateException If `"data"` is absent or null.
 */
inline fun <reified T> String.parseGraphQLAs(json: Json = jsonInstance): T {
    val envelope = parseAs<GraphQLResponse<T>>(json)
    val errors = envelope.errors
    if (!errors.isNullOrEmpty()) throw GraphQLException(errors.joinToString("\n") { it.message })
    return envelope.data ?: throw IllegalStateException("GraphQL response is missing the 'data' field")
}

/**
 * Thrown by [parseGraphQLAs] when the response contains a non-empty `"errors"` array.
 * [message] is the newline-joined concatenation of all error messages.
 */
class GraphQLException(message: String) : Exception(message)
