package eu.kanade.tachiyomi.extension.pt.argosscan

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private fun buildQuery(queryAction: () -> String) = queryAction().replace("%", "$")

val LOGIN_MUTATION_QUERY = buildQuery {
    """
        | mutation login(%email: String!, %password: String!) {
        |     login(loginInput: { email: %email, password: %password }) {
        |         token
        |     }
        | }
    """.trimMargin()
}

fun buildLoginMutationQueryPayload(email: String, password: String) = buildJsonObject {
    put("operationName", "login")
    put("query", LOGIN_MUTATION_QUERY)
    putJsonObject("variables") {
        put("email", email)
        put("password", password)
    }
}

val POPULAR_QUERY = buildQuery {
    """
        | query getProjects(
        |     %filters: FiltersExpression!,
        |     %orders: OrdersExpression!,
        |     %pagination: PaginationInput
        | ) {
        |     getProjects(
        |         orders: %orders,
        |         filters: %filters,
        |         pagination: %pagination
        |     ) {
        |         projects {
        |             id
        |             name
        |             cover
        |             type
        |             updateAt
        |             getChapters(order: { id: DESC }, skip: 0, take: 1) {
        |                 id
        |                 title
        |                 number
        |             }
        |         }
        |         count
        |         currentPage
        |         limit
        |         totalPages
        |     }
        | }
    """.trimMargin()
}

fun buildPopularQueryPayload(page: Int) = buildJsonObject {
    put("operationName", "getProjects")
    put("query", POPULAR_QUERY)
    putJsonObject("variables") {
        putJsonObject("filters") {
            putJsonObject("childExpressions") {
                putJsonObject("filters") {
                    put("field", "Project.id")
                    put("op", "GE")
                    putJsonArray("values") {
                        add("1")
                    }
                }
                put("operator", "AND")
            }
            put("operator", "AND")
        }
        putJsonObject("orders") {
            putJsonArray("orders") {
                addJsonObject {
                    put("field", "Project.views")
                    put("or", "DESC")
                }
            }
        }
        putJsonObject("pagination") {
            put("limit", 12)
            put("page", page)
        }
    }
}

fun buildSearchQueryPayload(query: String, page: Int) = buildJsonObject {
    put("operationName", "getProjects")
    put("query", POPULAR_QUERY)
    putJsonObject("variables") {
        putJsonObject("filters") {
            putJsonArray("filters") {
                addJsonObject {
                    put("field", "Project.name")
                    put("op", "LIKE")
                    putJsonArray("values") {
                        add(query)
                    }
                }
            }
            put("operator", "AND")
        }
        putJsonObject("orders") {
            putJsonArray("orders") {
                addJsonObject {
                    put("field", "Project.views")
                    put("or", "DESC")
                }
            }
        }
        putJsonObject("pagination") {
            put("limit", 10)
            put("page", page)
        }
    }
}

val MANGA_DETAILS_QUERY = buildQuery {
    """
        | query project(%id: Int!) {
        |     project(id: %id) {
        |         id
        |         name
        |         type
        |         description
        |         authors
        |         cover
        |         getChapters(order: { number: DESC }) {
        |             id
        |             number
        |             title
        |             createAt
        |         }
        |         getTags(order: { id: ASC }) {
        |             id
        |             name
        |         }
        |     }
        | }
    """.trimMargin()
}

fun buildMangaDetailsQueryPayload(id: Int) = buildJsonObject {
    put("operationName", "project")
    put("query", MANGA_DETAILS_QUERY)
    putJsonObject("variables") {
        put("id", id)
    }
}

val PAGES_QUERY = buildQuery {
    """
        | query getChapter(%id: String!) {
        |     getChapters(
        |         orders: {
        |             orders: { or: ASC, field: "Chapter.id" }
        |         }
        |         filters: {
        |             operator: AND,
        |             filters: [
        |                 { op: EQ, field: "Chapter.id", values: [%id] }
        |             ],
        |             childExpressions: {
        |                 operator: AND,
        |                 filters: {
        |                     op: GE,
        |                     field: "Project.id",
        |                     relationField: "Chapter.project",
        |                     values: ["1"]
        |                 }
        |             }
        |         }
        |     ) {
        |         chapters {
        |             id
        |             images
        |             project { id }
        |         }
        |     }
        | }
    """.trimMargin()
}

fun buildPagesQueryPayload(id: String) = buildJsonObject {
    put("operationName", "getChapter")
    put("query", PAGES_QUERY)
    putJsonObject("variables") {
        put("id", id)
    }
}
