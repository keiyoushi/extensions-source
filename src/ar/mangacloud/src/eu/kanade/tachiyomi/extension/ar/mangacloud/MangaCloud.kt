package eu.kanade.tachiyomi.extension.ar.mangacloud

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

@Source
abstract class MangaCloud : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request {
        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .addQueryParameter("key", API_KEY)
            .apply {
                val token = lastPageToken
                if (token != null) addQueryParameter("pageToken", token)
            }
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = FirestoreParser.parseList(response)
        lastPageToken = json.nextPageToken
        return MangasPage(json.mangas.map { it.smanga }, json.nextPageToken != null)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .addQueryParameter("key", API_KEY)
            .addQueryParameter("orderBy", "updatedAt desc")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val json = FirestoreParser.parseList(response)
        return MangasPage(json.mangas.map { it.smanga }, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        currentSearchQuery = query
        currentSortFilter = filters.firstInstanceOrNull<SortFilter>()?.selected.orEmpty()
        currentGenreFilter = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        currentStatusFilter = filters.firstInstanceOrNull<StatusFilter>()?.selected.orEmpty()

        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", "300")
            .addQueryParameter("key", API_KEY)
            .apply {
                when (currentSortFilter) {
                    "new" -> addQueryParameter("orderBy", "createdAt desc")
                    "updated" -> addQueryParameter("orderBy", "updatedAt desc")
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = currentSearchQuery.trim()
        val genre = currentGenreFilter
        val status = currentStatusFilter

        val allItems = mutableListOf<FirestoreParser.FirestoreMangaData>()
        var nextPageToken: String? = null
        var currentResponse = response

        do {
            val jsonString = currentResponse.body?.string() ?: break
            val json = JSONObject(jsonString)

            val newResponse = currentResponse.newBuilder()
                .body(jsonString.toResponseBody(null))
                .build()
            val parsed: FirestoreParser.FirestoreListResponse = FirestoreParser.parseList(newResponse)
            allItems.addAll(parsed.mangas)

            nextPageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }

            if (nextPageToken != null) {
                val urlBuilder = FIRESTORE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("pageSize", "300")
                    .addQueryParameter("key", API_KEY)
                    .addQueryParameter("pageToken", nextPageToken)
                    .apply {
                        when (currentSortFilter) {
                            "new" -> addQueryParameter("orderBy", "createdAt desc")
                            "updated" -> addQueryParameter("orderBy", "updatedAt desc")
                        }
                    }
                val newRequest = GET(urlBuilder.build(), headers)
                currentResponse = client.newCall(newRequest).execute()
            }
        } while (nextPageToken != null && currentResponse.isSuccessful)

        val filtered = allItems.filter { data ->
            val matchesQuery = if (query.isNotBlank()) {
                val q = query.lowercase()
                data.smanga.title.lowercase().contains(q) ||
                    data.altTitles.any { it.lowercase().contains(q) } ||
                    data.genres.any { it.lowercase().contains(q) } ||
                    data.smanga.description?.lowercase()?.contains(q) == true
            } else {
                true
            }
            val matchesGenre = if (genre.isNotBlank()) {
                data.genres.any { it.lowercase().contains(genre.lowercase()) }
            } else {
                true
            }
            val matchesStatus = if (status.isNotBlank()) {
                data.statusString == status
            } else {
                true
            }
            matchesQuery && matchesGenre && matchesStatus
        }

        return MangasPage(filtered.map { it.smanga }, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        val url = "$FIRESTORE_URL/$mangaId?key=$API_KEY".toHttpUrl()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = FirestoreParser.parseDetails(response)

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        val url = "$FIRESTORE_URL/$mangaId/chapters?pageSize=1000&key=$API_KEY&orderBy=index".toHttpUrl()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var nextPageToken: String? = null
        var currentResponse = response

        do {
            val jsonString = currentResponse.body?.string() ?: break
            val json = JSONObject(jsonString)

            val newResponse = currentResponse.newBuilder()
                .body(jsonString.toResponseBody(null))
                .build()
            val chapters = FirestoreParser.parseChapters(newResponse)
            allChapters.addAll(chapters)

            nextPageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }

            if (nextPageToken != null) {
                val pathSegments = currentResponse.request.url.pathSegments
                val chaptersIndex = pathSegments.indexOf("chapters")
                if (chaptersIndex < 1) break
                val mangaId = pathSegments[chaptersIndex - 1]

                val urlBuilder = "$FIRESTORE_URL/$mangaId/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("pageSize", "1000")
                    .addQueryParameter("key", API_KEY)
                    .addQueryParameter("orderBy", "index")
                    .addQueryParameter("pageToken", nextPageToken)

                val newRequest = GET(urlBuilder.build(), headers)
                currentResponse = client.newCall(newRequest).execute()
            }
        } while (nextPageToken != null && currentResponse.isSuccessful)

        allChapters.sortByDescending {
            it.chapter_number?.toString()?.toFloatOrNull() ?: 0f
        }

        return allChapters
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "${FIRESTORE_URL}/${chapter.url}?key=$API_KEY".toHttpUrl()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> = FirestoreParser.parsePages(response)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")
        return "$baseUrl/manga/$mangaId"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("/")
        if (parts.size >= 2) {
            return "$baseUrl/manga/${parts[0]}/${parts[1]}"
        }
        return "$baseUrl/manga/${chapter.url}"
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter("ترتيب حسب", sortingList),
        StatusFilter("الحالة", statusList),
        GenreFilter("التصنيف", genreList),
    )

    private var lastPageToken: String? = null
    private var currentSearchQuery: String = ""
    private var currentSortFilter: String = ""
    private var currentGenreFilter: String = ""
    private var currentStatusFilter: String = ""

    companion object {
        private const val API_KEY = "AIzaSyAvdmgz_r_d89Eo8JBs9vAjUAJR451bMYU"
        private const val FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/ninja-brew-crew-4d79c/databases/(default)/documents/cloudmangas"
        private const val PAGE_SIZE = 20

        private val sortingList = arrayOf(
            Pair("الأشهر", "popular"),
            Pair("آخر التحديثات", "updated"),
            Pair("إضافات جديدة", "new"),
        )

        private val statusList = arrayOf(
            Pair("الكل", ""),
            Pair("مستمر", "ongoing"),
            Pair("مكتمل", "completed"),
        )

        private val genreList = arrayOf(
            Pair("الكل", ""),
            Pair("إدارة المناطق", "إدارة المناطق"),
            Pair("إعادة احياء", "إعادة احياء"),
            Pair("إعادة بحث", "إعادة بحث"),
            Pair("إنتقام", "إنتقام"),
            Pair("إيسيكاي", "إيسيكاي"),
            Pair("ابراج", "ابراج"),
            Pair("اثاره", "اثاره"),
            Pair("اتصال بالعالم الخارجي", "اتصال بالعالم الخارجي"),
            Pair("اجناس", "اجناس"),
            Pair("احتجاج", "احتجاج"),
            Pair("اختفاء", "اختفاء"),
            Pair("ارتقاء", "ارتقاء"),
            Pair("ارواح", "ارواح"),
            Pair("ازياء", "ازياء"),
            Pair("اساطير", "اساطير"),
            Pair("اساطيز", "اساطيز"),
            Pair("اسبوعى", "اسبوعى"),
            Pair("استراتيجية", "استراتيجية"),
            Pair("اشباح", "اشباح"),
            Pair("اضطهاد", "اضطهاد"),
            Pair("اظطهاد", "اظطهاد"),
            Pair("اعادة احياء", "اعادة احياء"),
            Pair("اعمار", "اعمار"),
            Pair("اعمال", "اعمال"),
            Pair("اقتصاد", "اقتصاد"),
            Pair("اكاديميه", "اكاديميه"),
            Pair("اكشن", "اكشن"),
            Pair("الات", "الات"),
            Pair("الالوان الممتلئه", "الالوان الممتلئه"),
            Pair("البقاء علي قيد الحياه", "البقاء علي قيد الحياه"),
            Pair("الجانب المظلم من الحياه", "الجانب المظلم من الحياه"),
            Pair("الحريم العكسي", "الحريم العكسي"),
            Pair("الحياة المدرسيه", "الحياة المدرسيه"),
            Pair("الحياة اليومية", "الحياة اليومية"),
            Pair("الحيوانات الأليفة", "الحيوانات الأليفة"),
            Pair("الخيال العلمي", "الخيال العلمي"),
            Pair("السفر عبر الزمن", "السفر عبر الزمن"),
            Pair("العاب", "العاب"),
            Pair("العاب الكترونية", "العاب الكترونية"),
            Pair("العاب تقليدية", "العاب تقليدية"),
            Pair("العاب رعب", "العاب رعب"),
            Pair("العاب فيديو", "العاب فيديو"),
            Pair("العصور الوسطى", "العصور الوسطى"),
            Pair("الغموض", "الغموض"),
            Pair("الفتاة الوحش", "الفتاة الوحش"),
            Pair("الفنون القتالية", "الفنون القتالية"),
            Pair("الفنون العسكرية", "الفنون العسكرية"),
            Pair("المخالفون للقانون", "المخالفون للقانون"),
            Pair("النجاة", "النجاة"),
            Pair("الهة", "الهة"),
            Pair("الهه", "الهه"),
            Pair("الواقع الافتراضي", "الواقع الافتراضي"),
            Pair("اليات", "اليات"),
            Pair("امرأة شريرة", "امرأة شريرة"),
            Pair("اميرة", "اميرة"),
            Pair("انتقال", "انتقال"),
            Pair("انمى", "انمى"),
            Pair("ايتشى", "ايتشى"),
            Pair("ايسكاى", "ايسكاى"),
            Pair("ايشي", "ايشي"),
            Pair("بالغ", "بالغ"),
            Pair("بزنس", "بزنس"),
            Pair("بطل خارق", "بطل خارق"),
            Pair("بطل غير اعتيادي", "بطل غير اعتيادي"),
            Pair("بطل غير اعتيادى", "بطل غير اعتيادى"),
            Pair("بطل مجنون", "بطل مجنون"),
            Pair("بطل وحش", "بطل وحش"),
            Pair("بعد الكارثه", "بعد الكارثه"),
            Pair("بوليسي", "بوليسي"),
            Pair("تاريخ", "تاريخ"),
            Pair("تاريخى", "تاريخى"),
            Pair("تجاره", "تجاره"),
            Pair("تجسيد", "تجسيد"),
            Pair("تحديث", "تحديث"),
            Pair("تحري", "تحري"),
            Pair("تحقيق", "تحقيق"),
            Pair("تحقيقات", "تحقيقات"),
            Pair("تخطيط", "تخطيط"),
            Pair("تدريب", "تدريب"),
            Pair("تراجع", "تراجع"),
            Pair("تراجيدي", "تراجيدي"),
            Pair("ترويض", "ترويض"),
            Pair("ترويض وحوش", "ترويض وحوش"),
            Pair("تشويق", "تشويق"),
            Pair("تلوين رسم", "تلوين رسم"),
            Pair("تلوين رسمي", "تلوين رسمي"),
            Pair("تلوين هواة", "تلوين هواة"),
            Pair("تملك", "تملك"),
            Pair("تناسخ", "تناسخ"),
            Pair("تناسخ الارواح", "تناسخ الارواح"),
            Pair("تنانين", "تنانين"),
            Pair("تنايخ", "تنايخ"),
            Pair("ثأر", "ثأر"),
            Pair("جانحون", "جانحون"),
            Pair("جريمة", "جريمة"),
            Pair("جريمه", "جريمه"),
            Pair("جندر اسواب", "جندر اسواب"),
            Pair("جندر بندير", "جندر بندير"),
            Pair("جوسي", "جوسي"),
            Pair("جوسين", "جوسين"),
            Pair("جوسيه", "جوسيه"),
            Pair("حائز علي جائزة", "حائز علي جائزة"),
            Pair("حديث", "حديث"),
            Pair("حرب", "حرب"),
            Pair("حربي", "حربي"),
            Pair("حريم", "حريم"),
            Pair("حريم عكسي", "حريم عكسي"),
            Pair("حصرية", "حصرية"),
            Pair("حياة مدرسية", "حياة مدرسية"),
            Pair("حياة يومية", "حياة يومية"),
            Pair("حيوانات", "حيوانات"),
            Pair("حيوانات اليفه", "حيوانات اليفه"),
            Pair("خارق", "خارق"),
            Pair("خارق للطبيعه", "خارق للطبيعه"),
            Pair("خيار", "خيار"),
            Pair("خيال", "خيال"),
            Pair("خيال علمي", "خيال علمي"),
            Pair("خيالي", "خيالي"),
            Pair("داخل اللعبه", "داخل اللعبه"),
            Pair("داخل روايه", "داخل روايه"),
            Pair("دراما", "دراما"),
            Pair("دماء", "دماء"),
            Pair("دموى", "دموى"),
            Pair("ذكريات من عالم آخر", "ذكريات من عالم آخر"),
            Pair("راشد", "راشد"),
            Pair("رعاية اطفال", "رعاية اطفال"),
            Pair("رعب", "رعب"),
            Pair("روايات عربية", "روايات عربية"),
            Pair("روايه", "روايه"),
            Pair("رومانسي", "رومانسي"),
            Pair("رياضه", "رياضه"),
            Pair("رياضي", "رياضي"),
            Pair("زراعة", "زراعة"),
            Pair("زمكاني", "زمكاني"),
            Pair("زمنكاني", "زمنكاني"),
            Pair("زنزانات", "زنزانات"),
            Pair("زواج مدبر", "زواج مدبر"),
            Pair("زومبي", "زومبي"),
            Pair("ساموراي", "ساموراي"),
            Pair("ساموري", "ساموري"),
            Pair("سايكوباث", "سايكوباث"),
            Pair("سحر", "سحر"),
            Pair("سيرة ذاتية", "سيرة ذاتية"),
            Pair("سياسي", "سياسي"),
            Pair("سينين", "سينين"),
            Pair("شرطة", "شرطة"),
            Pair("شركه", "شركه"),
            Pair("شريحة من الحياة", "شريحة من الحياة"),
            Pair("شرير", "شرير"),
            Pair("شوجو", "شوجو"),
            Pair("شونين", "شونين"),
            Pair("شياطين", "شياطين"),
            Pair("شينين", "شينين"),
            Pair("صقل", "صقل"),
            Pair("طبخ", "طبخ"),
            Pair("طبي", "طبي"),
            Pair("طرد الارواح الشريره", "طرد الارواح الشريره"),
            Pair("عائلي", "عائلي"),
            Pair("عالم مختلف", "عالم مختلف"),
            Pair("عامل مكتبي", "عامل مكتبي"),
            Pair("عسكري", "عسكري"),
            Pair("عسكريه", "عسكريه"),
            Pair("عصر حديث", "عصر حديث"),
            Pair("عصور وسطى", "عصور وسطى"),
            Pair("علم نفس", "علم نفس"),
            Pair("علمي", "علمي"),
            Pair("عنف", "عنف"),
            Pair("عنف جنسي", "عنف جنسي"),
            Pair("عوالم", "عوالم"),
            Pair("عوده بالزمن", "عوده بالزمن"),
            Pair("غموض", "غموض"),
            Pair("فانتازا", "فانتازا"),
            Pair("فانتازيا", "فانتازيا"),
            Pair("فانتسي", "فانتسي"),
            Pair("فلسفه", "فلسفه"),
            Pair("فنتازيا", "فنتازيا"),
            Pair("فنون الدفاع عن النفس", "فنون الدفاع عن النفس"),
            Pair("فنون قتاليه", "فنون قتاليه"),
            Pair("فوق الطبيعه", "فوق الطبيعه"),
            Pair("فيكتوري", "فيكتوري"),
            Pair("قتال", "قتال"),
            Pair("قتالات", "قتالات"),
            Pair("قوة خارقة", "قوة خارقة"),
            Pair("كائنات فضائية", "كائنات فضائية"),
            Pair("كل الاعمار", "كل الاعمار"),
            Pair("كوميديا", "كوميديا"),
            Pair("كوميدى", "كوميدى"),
            Pair("كوميك", "كوميك"),
            Pair("كوما-4", "كوما-4"),
            Pair("لعبه", "لعبه"),
            Pair("مأساوي", "مأساوي"),
            Pair("مؤامرات", "مؤامرات"),
            Pair("ما بعد نهاية العالم", "ما بعد نهاية العالم"),
            Pair("مأساة", "مأساة"),
            Pair("مافيا", "مافيا"),
            Pair("مانا", "مانا"),
            Pair("مانجا", "مانجا"),
            Pair("مانجا ملونه", "مانجا ملونه"),
            Pair("مانها", "مانها"),
            Pair("مانهوا", "مانهوا"),
            Pair("متحول", "متحول"),
            Pair("مجموعة قصص", "مجموعة قصص"),
            Pair("مدرسه", "مدرسه"),
            Pair("مدرسي", "مدرسي"),
            Pair("مستذئب", "مستذئب"),
            Pair("مصاصي الدماء", "مصاصي الدماء"),
            Pair("معالج", "معالج"),
            Pair("مغامرة", "مغامرة"),
            Pair("مغني", "مغني"),
            Pair("مقتبسة", "مقتبسة"),
            Pair("مقطع طولي", "مقطع طولي"),
            Pair("ملائكة", "ملائكة"),
            Pair("ملونه", "ملونه"),
            Pair("ممالك", "ممالك"),
            Pair("موريم", "موريم"),
            Pair("موسيقى", "موسيقى"),
            Pair("ميكا", "ميكا"),
            Pair("ناضج", "ناضج"),
            Pair("نبالة", "نبالة"),
            Pair("نبلاء", "نبلاء"),
            Pair("نظام", "نظام"),
            Pair("نفسي", "نفسي"),
            Pair("نهاية العالم", "نهاية العالم"),
            Pair("نينجا", "نينجا"),
            Pair("هندسة", "هندسة"),
            Pair("هواه", "هواه"),
            Pair("هوس", "هوس"),
            Pair("واقع افتراضي", "واقع افتراضي"),
            Pair("واقعى", "واقعى"),
            Pair("ويبتون", "ويبتون"),
            Pair("وحوش", "وحوش"),
            Pair("ون شوت", "ون شوت"),
            Pair("ويب تون", "ويب تون"),
            Pair("يقاء", "يقاء"),
        )
    }
}
