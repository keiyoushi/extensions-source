package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class E621 :
    HttpSource(),
    ConfigurableSource {

    override val name: String = "e621"
    override val baseUrl: String = "https://e621.net"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

    override fun getFilterList(): FilterList = getE621FilterList(preferences.categoryPref)

    override fun setupPreferenceScreen(screen: PreferenceScreen) = setupE621PreferenceScreen(screen)

    private val preferences: SharedPreferences by getPreferencesLazy()

    // e621 needs a custom User-Agent header
    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "E621/1.4.${BuildConfig.VERSION_CODE} Keiyoushi (https://github.com/keiyoushi/extensions-source)")

    private val artistFilter = setOf(
        "conditional_dnp",
        "unknown_artist",
        "third-party_edit",
        "sound_warning",
        "anonymous_artist",
    )

    // common implied or arbitrary tags
    private val tagFilter = hashSetOf(
        // species
        "accipitrid", "accipitriform", "ailurid", "alien_humanoid", "alligatorid",
        "ambiguous_species", "amphibian", "anatid", "animal_humanoid", "animate_inanimate",
        "anseriform", "aquatic", "aquatic_humanoid", "arachnid", "arthropod", "asinus",
        "avian", "boss_monster_(undertale)", "bovid", "bovid_humanoid", "bovine", "canid",
        "canid_demon", "canid_humanoid", "canine", "canine_humanoid", "canis", "caprine",
        "cattle", "cat_humanoid", "cephalopod", "cephalopod_humanoid", "cervine", "cetacean",
        "corvid", "crocodilian", "demon_humanoid", "domestic_ferret", "draconcopode",
        "dromaeosaurid", "earth_pony", "eeveelution", "elemental_creature",
        "elemental_humanoid", "equid", "equine", "eulipotyphlan", "felid", "feline",
        "feline_humanoid", "felis", "flora_fauna", "fox_humanoid", "galliform",
        "generation_1_pokemon", "generation_2_pokemon", "generation_3_pokemon",
        "generation_4_pokemon", "generation_5_pokemon", "generation_6_pokemon",
        "generation_7_pokemon", "generation_8_pokemon", "generation_9_pokemon", "giraffid",
        "haplorhine", "horned_humanoid", "humanoid", "hunting_dog", "hymenopteran",
        "lagomorph", "lagomorph_humanoid", "legendary_pokemon", "lepidopteran", "leporid",
        "leporid_humanoid", "macropod", "mammal", "mammal_humanoid", "mammal_taur",
        "marsupial", "mega_evolution", "mollusk", "mollusk_humanoid", "monotreme", "murid",
        "murine", "mustelid", "musteline", "mythological_avian", "mythological_canine",
        "mythological_creature", "mythological_equine", "mythological_scalie",
        "ornithischian", "oryctolagus", "oscine", "pantherine", "passerine", "phasianid",
        "pinscher", "prehistoric_species", "primate", "procyonid", "rabbit_humanoid",
        "regional_form_(pokemon)", "reptile", "retriever", "robot_humanoid", "rodent",
        "saurischian", "scalie", "sciurid", "shiba_inu", "shiny_pokemon", "spitz", "suid",
        "suine", "tailed_humanoid", "taur", "true_musteline", "werecanine", "werecreature",
        "yokai",
        // general
        "3_toes", "4_fingers", "4_toes", "5_fingers", "accessory", "animal_penis", "areola",
        "armwear", "barefoot", "beak", "bed", "belly", "biceps", "black_body",
        "black_clothing", "black_eyes", "black_fur", "black_hair", "black_nose",
        "blonde_hair", "blue_body", "blue_eyes", "blue_fur", "blue_hair", "blush",
        "blush_lines", "bodily_fluids", "border", "bottomwear", "brown_body", "brown_eyes",
        "brown_fur", "brown_hair", "butt", "canine_genitalia", "canine_penis", "clitoris",
        "clothing", "collar", "container", "countershading", "cutie_mark",
        "detailed_background", "dipstick_tail", "ear_piercing", "ear_ring", "electronics",
        "equine_penis", "eyebrows", "eyelashes", "eyes_closed", "facial_hair",
        "facial_piercing", "feathered_wings", "fingers", "finger_claws", "footwear",
        "front_view", "furniture", "genitals", "genital_fluids", "gesture", "glans", "gloves",
        "grass", "green_body", "green_eyes", "grey_background", "grey_body", "grey_fur",
        "grin", "hair", "hair_accessory", "half-closed_eyes", "handwear", "happy", "hat",
        "headwear", "heart_symbol", "holding_object", "holidays", "hooves", "horn",
        "humanoid_hands", "humanoid_penis", "inside", "jewelry", "kneeling", "legwear",
        "long_hair", "looking_at_another", "looking_back", "lying", "machine",
        "membrane_(anatomy)", "membranous_wings", "multicolored_hair", "muscular_anthro",
        "narrowed_eyes", "navel", "necklace", "nude_anthro", "one_eye_closed", "on_bed",
        "orange_body", "orange_fur", "pants", "pawpads", "paws", "pecs", "penile",
        "penile_penetration", "pillow", "pink_body", "pink_hair", "pink_nose", "plant",
        "pointy_ears", "pupils", "purple_body", "purple_eyes", "purple_hair", "rear_view",
        "red_body", "red_eyes", "red_hair", "scales", "sheath", "shirt", "shoes", "shorts",
        "short_hair", "simple_background", "sitting", "skirt", "sky", "smile", "soles",
        "sound_effects", "speech_bubble", "spikes", "spots", "standing", "stripes", "tail",
        "tan_fur", "teeth", "text", "toe_claws", "topwear", "translucent", "tree",
        "two_tone_body", "two_tone_fur", "vaginal", "vaginal_fluids", "water", "whiskers",
        "white_background", "white_body", "white_clothing", "white_fur", "white_hair",
        "yellow_body", "yellow_eyes", "yellow_fur",
        // artist
        "conditional_dnp", "sound_warning",
        // added through testing
        "underwear", "sniffing", "animal_genitalia", "erection", "tongue", "page_number",
        "countershade_torso", "motion_lines", "tunic", "panel_skew", "interior_background",
        "headgear", "blue_sky", "5_toes", "4_toes", "3_toes", "onomatopoeia", "color_coded",
        "color_coded_speech_bubble", "patreon", "polygonal_speech_bubble", "blockage_(layout)",
    )

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        val popularMode = preferences.popularModePref
        val firstEnd = preferences.firstEndPref

        // A little hacky, but it helps unify things
        return searchMangaRequest(
            page,
            "",
            FilterList(
                ModeFilter(getDefaultModeIndex(searchMode)),
                CategoryFilter(getDefaultCategoryIndex(category)),
                OrderFilter(getDefaultOrderIndex("post_count")),
                TagsFilter("$popularMode $firstEnd".trim()),
            ),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        var scoreThresh = preferences.scoreThreshPref

        // A little hacky, but it helps unify things
        return searchMangaRequest(
            page,
            "",
            FilterList(
                ModeFilter(getDefaultModeIndex(searchMode)),
                CategoryFilter(getDefaultCategoryIndex(category)),
                OrderFilter(getDefaultOrderIndex("created_at")),
                TagsFilter("order:id_desc score:>=$scoreThresh"),
            ),
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")

        var mode = "pools.json"
        var category = ""
        var order = "updated_at"
        var activeOnly = false
        var description = ""

        // TODO: Get this to work. Requires implementing posts as a manga url.
        // var tagsMandatory = "( ~inpool:true ~( comic tall_image ) ) -video status:any"
        var tagsMandatory = "inpool:true -video status:any"
        var orderTag = ""
        var tags = ""
        var firstPage = false
        var endPage = false
        var dateTag = ""

        var blacklist = preferences.blacklistPref
        var whitelist = preferences.whitelistPref

        filters.forEach { filter ->
            when (filter) {
                // Keep these for popularMangaRequest and etc. compatibility
                is ModeFilter -> mode = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                is OrderFilter -> order = filter.toUriPart()
                is TagsFilter -> tags = filter.state.trim()
                // is ActiveOnlyFilter -> activeOnly = filter.state
                // is DescriptionFilter -> description = filter.state.trim()
                is PoolGroupFilter -> {
                    category = filter.getCategory()
                    order = filter.getOrder()
                    activeOnly = filter.getActiveOnly()
                    description = filter.getDescription()
                }
                is TagGroupFilter -> {
                    orderTag = filter.getOrderTag()
                    tags = filter.getTags()
                    firstPage = filter.getFirstPage()
                    endPage = filter.getEndPage()
                    dateTag = filter.getDate()
                }
                else -> {}
            }
        }

        url.addPathSegment(mode)

        if (mode == "pools.json") {
            // Pools
            url.addQueryParameter("search[order]", order).addQueryParameter("limit", "24")
            if (category.isNotEmpty()) url.addQueryParameter("search[category]", category)
            if (activeOnly) url.addQueryParameter("search[is_active]", "true")
            if (query.isNotEmpty()) {
                val search = "*${query.trim().replace(" ", "_")}*"
                url.addQueryParameter("search[name_matches]", search)
            }
            if (description.isNotEmpty()) {
                url.addQueryParameter("search[description_matches]", description)
            }
        } else {
            // Posts
            tags = "$tagsMandatory $whitelist $blacklist $tags".trim()
            if (query.isNotEmpty()) {
                val search = "*${query.trim().replace(" ", "_")}*"
                tags = "$tags $search"
            }
            if (orderTag.isNotEmpty()) tags = "order:$orderTag $tags"
            if (dateTag.isNotEmpty()) tags = "date:$dateTag $tags"
            if (firstPage && endPage) {
                tags = "$tags ( ~first_page ~end_page )"
            } else if (firstPage) {
                tags = "$tags first_page"
            } else if (endPage) {
                tags = "$tags first_page"
            }
            // I've quadrupled the limit due to increased requests and duplicate
            // culling while in this mode. Helps to reduce API requests
            url.addQueryParameter("limit", "96")
            url.addQueryParameter("tags", "$tags")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        var mode = response.request.url.encodedPath

        if (mode == "/pools.json") {
            return parsePoolList(response)
        } else if (mode == "/posts.json") {
            return parsePostsList(response)
        }
        return MangasPage(emptyList(), false)
    }

    private fun parsePoolList(response: Response): MangasPage {
        val pools = response.parseAs<List<Pool>>()
        // BUG: false alarm 'No results found' when the total number of posts is a multiple of 24
        return parsePoolListDirect(pools, pools.size >= 24)
    }

    private fun parsePostsList(response: Response): MangasPage {
        val posts = response.parseAs<PostsResponse>().posts
        val poolIds = posts.flatMap { it.poolIds }
        val pools = batchFetchPools(poolIds)
        return parsePoolListDirect(pools, posts.size >= 96)
    }

    private fun parsePoolListDirect(pools: List<Pool>, hasNextPage: Boolean): MangasPage {
        val thumbnailMap = batchFetchPostSamples(
            pools.mapNotNull { it.postIds.firstOrNull() },
        ).takeIf { it.isNotEmpty() } ?: emptyMap()

        val poolList = pools.map { pool ->
            SManga.create().apply {
                url = pool.id.toString()
                title = pool.name.replace("_", " ")
                thumbnail_url = pool.postIds.firstOrNull()
                    ?.let { thumbnailMap[it] }
            }
        }

        return MangasPage(poolList, hasNextPage)
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val poolId = manga.url
        val url = "$baseUrl/pools/$poolId.json"
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val pool = response.parseAs<Pool>()

        // fetch first 40 posts for common tags and artist
        // cut off first 20% to try to get more relevant tags
        val cutoff = (pool.postIds.size * 0.2).toInt()
        val posts = if (preferences.betterDetailsPref) {
            batchFetchPosts(pool.postIds.drop(cutoff).take(40))
        } else {
            emptyList<Post>()
        }

        val artists = posts.flatMap { it.tags.artist }.toSet()
        val rating = when {
            posts.any { it.rating == "e" } -> "rating:Explicit, "
            posts.any { it.rating == "q" } -> "rating:Questionable, "
            posts.any { it.rating == "s" } -> "rating:Safe, "
            else -> ""
        }

        val medScore = posts.map { it.score.total }.sorted().getOrNull(posts.size / 2) ?: -99999
        val score = if (medScore != -99999) "score:>${medScore - 1}, " else ""

        // // Pick all tags that occur in more than 50% of the sample posts
        // val tags = posts.flatMap {
        //     it.tags.general +
        //     // it.tags.artist +
        //     it.tags.copyright +
        //     it.tags.character +
        //     it.tags.species +
        //     it.tags.lore
        // }.groupingBy { it }.eachCount()
        //     .filter { it.value >= posts.size / 2 }.toList() // >50% of posts have tag
        //     .sortedByDescending { it.second } // sort by count
        //     // .sortedBy { it.first } // sort alphabetically
        //     .map { it.first }

        // A more complicated tag selecting algorithm
        // TODO: A bit hefty. Might it be simplified?
        val tags = posts.flatMap { post ->
            listOf(
                // Tags show up in this order:
                post.tags.lore.map { "lore" to it },
                post.tags.general.map { "general" to it },
                post.tags.species.map { "species" to it },
                post.tags.character.map { "character" to it },
                post.tags.copyright.map { "copyright" to it },
            ).flatten()
        }.groupBy({ it.first }, { it.second })
            .map { (genre, tags) ->
                genre to tags.groupingBy { it }
                    .eachCount()
                    .filter { it.key !in tagFilter }
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key }
            }.flatMap { (genre, tags) ->
                when (genre) {
                    "general" -> tags.take(15)
                    "copyright" -> tags.take(3)
                    "character" -> tags.take(5)
                    "species" -> tags.take(5)
                    "lore" -> tags.take(5)
                    else -> emptyList()
                }
            }

        return SManga.create().apply {
            url = pool.id.toString()
            title = pool.name.replace("_", " ")
            description = if (pool.description.length > 400) {
                pool.description.take(400) + " ..."
            } else {
                pool.description
            }

            status = when (pool.isActive) {
                true -> SManga.ONGOING
                false -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = "$rating$score" + tags.joinToString(", ")
            author = artists.filter { it !in tagFilter }.joinToString(", ")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/pools/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val poolId = manga.url
        val url = "$baseUrl/pools/$poolId.json"
        return GET(url, headers)
    }

    // A bit big on the chonk chart. TODO: Maybe break it up or simplify it?
    override fun chapterListParse(response: Response): List<SChapter> {
        val pool = response.parseAs<Pool>()
        val postIds = pool.postIds
        val title = pool.name.replace("_", " ")

        if (postIds.isEmpty()) return emptyList<SChapter>()

        val betterDetailsPref = preferences.betterDetailsPref
        val splitChaptersPref = preferences.splitChaptersPref
            .takeIf {
                preferences.splitChaptersPref != "chapters" || betterDetailsPref
            } ?: "merged"

        return if (splitChaptersPref == "chapters") {
            // fetch all posts for chapter detection
            val posts = batchFetchPosts(postIds)

            val poolIds = posts.flatMap { it.poolIds }.toSet().toList()
            val subPools = batchFetchPools(poolIds)
                .associate { it.id to it }
                .filterValues { it.postIds.size <= pool.postIds.size && it.id != pool.id }

            var usedPools = mutableMapOf<Int, Pool>()

            // TODO: Plenty of room for optimization
            var n: Int = 0
            posts.mapNotNull { post ->
                val isInUsedPool: Boolean = post.poolIds.any { it in usedPools }

                val minPoolFirstInId: Int = (!isInUsedPool).let {
                    subPools.filter { it.key !in usedPools && post.id in it.value.postIds.take(5) }
                        .minByOrNull { it.value.postIds.size }?.key
                } ?: 0

                when {
                    // Skip Post
                    isInUsedPool -> null

                    // Add Pool
                    minPoolFirstInId > 0 -> {
                        val subPool = subPools[minPoolFirstInId]!!
                        var chapterTitle = subPool.name.replace("_", " ")
                        if (chapterTitle == title) chapterTitle = "Chapter $n"

                        usedPools.put(subPool.id, subPool)

                        SChapter.create().apply {
                            name = "$chapterTitle (${subPool.postIds.size} pages)"
                            url = "/pools/${subPool.id}"
                            chapter_number = (++n).toFloat()
                            date_upload = parseDate(subPool.updatedAt)
                        }
                    }

                    // Add Post
                    else -> SChapter.create().apply {
                        name = "Post #${post.id}"
                        url = "/posts/${post.id}"
                        chapter_number = (++n).toFloat()
                        date_upload = parseDate(post.createdAt)
                    }
                }
                // If more than half of the chapters are not pools, then merge into single pool
            }.reversed().takeIf { it.size / 2 <= usedPools.size } ?: listOf(
                SChapter.create().apply {
                    name = "\u200B$title (${pool.postIds.size} pages)"
                    url = "/pools/${pool.id}"
                    chapter_number = 1f
                    date_upload = parseDate(pool.updatedAt)
                },
            )
        } else if ((splitChaptersPref == "posts")) {
            postIds.mapIndexed { index, postId ->
                SChapter.create().apply {
                    name = "Post #$postId"
                    url = "/posts/$postId"
                    chapter_number = (index + 1).toFloat()
                    // date_upload = if (index == 0) parseDate(pool.updatedAt) else 0L
                    date_upload = parseDate(pool.updatedAt)
                }
            }.reversed()
        } else if (splitChaptersPref == "merged") {
            listOf(
                SChapter.create().apply {
                    name = "Pool #${pool.id} (${postIds.size} pages)"
                    url = "/pools/${pool.id}"
                    chapter_number = 1f
                    date_upload = parseDate(pool.updatedAt)
                },
            )
        } else {
            emptyList<SChapter>()
        }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()

        return if (chapterUrl.pathSegments.getOrNull(0) == "posts") {
            val postId = chapterUrl.pathSegments.last().toIntOrNull()
            val url = "$baseUrl/posts.json".toHttpUrl().newBuilder().apply {
                if (postId != null) {
                    addQueryParameter("tags", "id:$postId")
                    addQueryParameter("limit", "1")
                }
            }.build()
            GET(url, headers)
        } else if (chapterUrl.pathSegments.getOrNull(0) == "pools") {
            val poolId = chapterUrl.pathSegments.last()
            val url = "$baseUrl/pools/$poolId.json"
            GET(url, headers)
        } else {
            GET("", headers)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url

        // Single post chapter (split chapters mode)
        if (url.encodedPath == "/posts.json") {
            val post = response.parseAs<PostsResponse>().posts.firstOrNull()
            val imageUrl = when {
                post == null -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted" // Not returned by API
                isPostDeleted(post) -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted"
                else -> extractImageUrl(post) ?: "https://placehold.co/256x256/cccccc/f66151.jpg?text=No%20Image"
            }
            return listOf(Page(0, imageUrl = imageUrl))
        }

        // Pool chapter with all pages
        val postIds = response.parseAs<Pool>().postIds
        if (postIds.isEmpty()) return emptyList()

        val posts = batchFetchPosts(postIds)
        val postMap = posts.associateBy { it.id }

        return postIds.mapIndexed { index, postId ->
            val post = postMap[postId]
            val imageUrl = when {
                post == null -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted" // Not returned by API
                isPostDeleted(post) -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted"
                else -> extractImageUrl(post) ?: "https://placehold.co/256x256/cccccc/f66151.jpg?text=No%20Image"
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Helpers

    private fun isPostDeleted(post: Post): Boolean = post.flags.deleted

    private fun extractThumbnailUrl(post: Post): String? {
        // Preview (smallest, fastest to load)
        post.preview.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Sample
        post.sample.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Full Resolution
        post.file.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        return null
    }

    private fun extractImageUrl(post: Post): String? {
        // Full Resolution (best for reading. can be absurd resolution)
        post.file.url
            ?.takeIf {
                preferences.fullResolution ||
                    !post.sample.has ||
                    post.sample.width < 800 ||
                    post.sample.height < 1200
            }
            ?.let {
                if (it != "null" && it.isNotEmpty()) return it
            }

        // Sample (usually good enough quality for reading)
        post.sample.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Preview
        post.preview.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        return null
    }

    private fun batchFetchPosts(postIds: List<Int>): List<Post> {
        if (postIds.isEmpty()) return emptyList()

        // increased chunk size from 40 to reduce requests for large pools
        return postIds.chunked(200).flatMap { chunk ->
            runCatching {
                val tagQuery = "status:all id:" + chunk.joinToString(",")
                val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
                    .addQueryParameter("tags", tagQuery)
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                val data = client.newCall(GET(url, headers)).execute()
                    .parseAs<PostsResponse>()

                data.posts.sortedBy { chunk.indexOf(it.id) }
            }.getOrDefault(emptyList())
        }
    }

    private fun batchFetchPools(poolIds: List<Int>): List<Pool> {
        if (poolIds.isEmpty()) return emptyList()

        return poolIds.distinct().chunked(100).flatMap { chunk ->
            runCatching {
                val url = "$baseUrl/pools.json".toHttpUrl().newBuilder()
                    .addQueryParameter("search[order]", "id_desc")
                    .addQueryParameter("search[id]", chunk.joinToString(","))
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                val data = client.newCall(GET(url, headers)).execute()
                    .parseAs<List<Pool>>()

                data.sortedBy { chunk.indexOf(it.id) }
            }.getOrDefault(emptyList())
        }
    }

    private fun batchFetchPostSamples(postIds: List<Int>): Map<Int, String> {
        if (postIds.isEmpty()) return emptyMap()

        return batchFetchPosts(postIds).mapNotNull { post ->
            extractThumbnailUrl(post)?.let { post.id to it }
        }.toMap()
    }

    private fun parseDate(dateStr: String): Long = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).tryParse(dateStr)
}
