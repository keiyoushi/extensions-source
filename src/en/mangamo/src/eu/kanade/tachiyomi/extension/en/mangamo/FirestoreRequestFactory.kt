package eu.kanade.tachiyomi.extension.en.mangamo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FirestoreRequestFactory(
    private val helper: MangamoHelper,
    private val auth: MangamoAuth,
) {

    open class DocumentQuery {
        var fields = listOf<String>()
    }

    class CollectionQuery : DocumentQuery() {

        var filter: Filter? = null
        var orderBy: List<OrderByTerm>? = null

        // Firestore supports cursors, but this is simpler and probably good enough
        var limit: Int? = null
        var offset: Int? = null

        class OrderByTerm(private val field: String, private val direction: Direction) {
            enum class Direction { ASCENDING, DESCENDING }

            fun toJsonString() = """{"direction":"$direction","field":{"fieldPath":"$field"}}"""
        }

        fun ascending(field: String) =
            OrderByTerm(field, OrderByTerm.Direction.ASCENDING)
        fun descending(field: String) =
            OrderByTerm(field, OrderByTerm.Direction.DESCENDING)

        sealed interface Filter {
            fun toJsonString(): String

            class CompositeFilter(private val op: Operator, private val filters: List<Filter>) : Filter {
                enum class Operator { AND, OR }

                override fun toJsonString(): String =
                    """{"compositeFilter":{"op":"$op","filters":[${filters.joinToString { it.toJsonString() }}]}}"""
            }

            class FieldFilter(private val fieldName: String, private val op: Operator, private val value: Any?) : Filter {
                enum class Operator {
                    LESS_THAN,
                    LESS_THAN_OR_EQUAL,
                    GREATER_THAN,
                    GREATER_THAN_OR_EQUAL,
                    EQUAL,
                    NOT_EQUAL,
                    ARRAY_CONTAINS,
                    IN,
                    ARRAY_CONTAINS_ANY,
                    NOT_IN,
                }

                override fun toJsonString(): String {
                    val valueTerm =
                        when (value) {
                            null -> "{\"nullValue\":null}"
                            is Int -> "{\"integerValue\":$value}"
                            is Double -> "{\"doubleValue\":$value}"
                            is String -> "{\"stringValue\":\"$value\"}"
                            is Boolean -> "{\"booleanValue\":$value}"
                            else -> throw Exception("${value.javaClass} not supported in field filters")
                        }
                    return """{"fieldFilter":{"op":"$op","field":{"fieldPath":"$fieldName"},"value":$valueTerm}}"""
                }
            }

            class UnaryFilter(private val fieldName: String, private val op: Operator) : Filter {
                enum class Operator { IS_NAN, IS_NULL, IS_NOT_NAN, IS_NOT_NULL }

                override fun toJsonString(): String {
                    return """{"unaryFilter":{"op":"$op","field":{"fieldPath":"$fieldName"}}}"""
                }
            }
        }

        fun and(vararg filters: Filter) =
            Filter.CompositeFilter(Filter.CompositeFilter.Operator.AND, filters.toList())
        fun or(vararg filters: Filter) =
            Filter.CompositeFilter(Filter.CompositeFilter.Operator.OR, filters.toList())
        fun isLessThan(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.LESS_THAN, value)
        fun isLessThanOrEqual(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.LESS_THAN_OR_EQUAL, value)
        fun isGreaterThan(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.GREATER_THAN, value)
        fun isGreaterThanOrEqual(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.GREATER_THAN_OR_EQUAL, value)
        fun isEqual(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.EQUAL, value)
        fun isNotEqual(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.NOT_EQUAL, value)
        fun contains(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.ARRAY_CONTAINS, value)
        fun isIn(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.IN, value)
        fun containsAny(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.ARRAY_CONTAINS_ANY, value)
        fun isNotIn(fieldName: String, value: Any?) =
            Filter.FieldFilter(fieldName, Filter.FieldFilter.Operator.NOT_IN, value)
        fun isNaN(fieldName: String) =
            Filter.UnaryFilter(fieldName, Filter.UnaryFilter.Operator.IS_NAN)
        fun isNull(fieldName: String) =
            Filter.UnaryFilter(fieldName, Filter.UnaryFilter.Operator.IS_NULL)
        fun isNotNaN(fieldName: String) =
            Filter.UnaryFilter(fieldName, Filter.UnaryFilter.Operator.IS_NOT_NAN)
        fun isNotNull(fieldName: String) =
            Filter.UnaryFilter(fieldName, Filter.UnaryFilter.Operator.IS_NOT_NULL)
    }

    fun getDocument(path: String, query: DocumentQuery.() -> Unit = {}): Request {
        val queryInfo = DocumentQuery()
        query(queryInfo)

        val urlBuilder = "${MangamoConstants.FIRESTORE_API_BASE_PATH}/$path".toHttpUrl().newBuilder()

        for (field in queryInfo.fields) {
            urlBuilder.addQueryParameter("mask.fieldPaths", field)
        }

        val headers = Headers.Builder()
            .add("Authorization", "Bearer ${auth.getIdToken()}")
            .build()

        return GET(urlBuilder.build(), headers)
    }

    private fun deconstructCollectionPath(path: String): Pair<String, String> {
        val pivot = path.lastIndexOf('/')
        if (pivot == -1) {
            return Pair("", path)
        }
        return Pair(path.substring(0, pivot), path.substring(pivot + 1))
    }

    fun getCollection(
        fullPath: String,
        query: CollectionQuery.() -> Unit = {},
    ): Request {
        val queryInfo = CollectionQuery()
        query(queryInfo)

        val structuredQuery = mutableMapOf<String, String?>()

        val (path, collectionId) = deconstructCollectionPath(fullPath)

        structuredQuery["from"] = "{\"collectionId\":\"$collectionId\"}"

        if (queryInfo.fields.isNotEmpty()) {
            structuredQuery["select"] = "{\"fields\":[${queryInfo.fields.joinToString {
                "{\"fieldPath\":\"$it\"}"
            }}]}"
        }

        if (queryInfo.filter != null) {
            structuredQuery["where"] = queryInfo.filter!!.toJsonString()
        }

        if (queryInfo.orderBy != null) {
            structuredQuery["orderBy"] = "[${queryInfo.orderBy!!.joinToString { it.toJsonString() }}]"
        }

        structuredQuery["offset"] = queryInfo.offset?.toString()
        structuredQuery["limit"] = queryInfo.limit?.toString()

        val headers = helper.jsonHeaders.newBuilder()
            .add("Authorization", "Bearer ${auth.getIdToken()}")
            .build()

        val body = "{\"structuredQuery\":{${
        structuredQuery.entries
            .filter { it.value != null }
            .joinToString { "\"${it.key}\":${it.value}" }
        }}}".toRequestBody()

        return POST("${MangamoConstants.FIRESTORE_API_BASE_PATH}/$path:runQuery", headers, body)
    }
}
