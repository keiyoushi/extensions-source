package eu.kanade.tachiyomi.extension.en.voyceme

val POPULAR_QUERY: String = $$"""
    query($limit: Int, $offset: Int) {
        voyce_series(
            where: {
                publish: { _eq: 1 },
                type: { id: { _in: [2, 4] } }
            },
            order_by: [{ views_counts: { count: desc_nulls_last } }],
            limit: $limit,
            offset: $offset
        ) {
            id
            slug
            thumbnail
            title
        }
    }
""".trimIndent()

val LATEST_QUERY: String = $$"""
    query($limit: Int, $offset: Int) {
        voyce_series(
            where: {
                publish: { _eq: 1 },
                type: { id: { _in: [2, 4] } }
            },
            order_by: [{ updated_at: desc }],
            limit: $limit,
            offset: $offset
        ) {
            id
            slug
            thumbnail
            title
        }
    }
""".trimIndent()

val SEARCH_QUERY: String = $$"""
    query($searchTerm: String!, $limit: Int, $offset: Int) {
        voyce_series(
            where: {
                publish: { _eq: 1 },
                type: { id: { _in: [2, 4] } },
                title: { _ilike: $searchTerm }
            },
            order_by: [{ views_counts: { count: desc_nulls_last } }],
            limit: $limit,
            offset: $offset
        ) {
            id
            slug
            thumbnail
            title
        }
    }
""".trimIndent()

val UPDATES_QUERY: String = $$"""
    query($slug: String!) {
        voyce_series(
            where: {
                publish: { _eq: 1 },
                type: { id: { _in: [2, 4] } },
                slug: { _eq: $slug }
            },
            limit: 1,
        ) {
            id
            slug
            thumbnail
            title
            description
            status
            author { username }
            genres(order_by: [{ genre: { title: asc } }]) {
                genre { title }
            }

            chapters(order_by: [{ created_at: desc }]) {
                id
                title
                created_at
            }
        }
    }
""".trimIndent()

val PAGES_QUERY: String = $$"""
    query($chapterId: Int!) {
        voyce_chapter_images(
            where: { chapter_id: { _eq: $chapterId } },
            order_by: { sort_order: asc }
        ) {
            image
        }
    }
""".trimIndent()
