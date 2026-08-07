package eu.kanade.tachiyomi.extension.all.xcomic

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class XCOMIC :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override val supportsLatest = true

    // ========================= Popular & Latest ==========================
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.LATEST)

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val idMatch = when {
            query.startsWith("https://") -> {
                val id = urlIdRegex.find(query)?.groupValues?.get(1)
                    ?: throw Exception("Unknown url")
                idQueryRegex.matchEntire("id:$id")
            }
            else -> idQueryRegex.matchEntire(query.trim())
        }
        if (idMatch != null) {
            val id = idMatch.groupValues[1]
            val apiVariables = ApiComicNodeVariables(id)
            val response = client.newCall(graphQLRequest(apiVariables, COMIC_NODE_QUERY)).await()
            return MangasPage(listOf(parseMangaDetails(response)), false)
        }

        var sort: String? = null
        var letterMode = false
        var contentRating = emptyList<String>()
        var types = emptyList<String>()
        var demographics = emptyList<String>()
        val incGenres = mutableListOf<String>()
        val excGenres = mutableListOf<String>()
        var incGenresMode: String? = null
        var excGenresMode: String? = null
        var releaseYearMin: Int? = null
        var releaseYearMax: Int? = null
        var incOLangs = emptyList<String>()
        var incTLangs = if (lang == "all") emptyList() else listOf(lang)
        var origStatus = ""
        var uploadStatus = ""
        var chapCount = ""

        filters.forEach { filter ->
            when (filter) {
                is LetterFilter -> letterMode = (filter.state == 1)
                is ContentRatingFilter -> contentRating = filter.selected
                is TypeFilter -> types = filter.selected
                is DemographicFilter -> demographics = filter.selected
                is FormatFilter -> {
                    incGenres.addAll(filter.included)
                    excGenres.addAll(filter.excluded)
                }
                is GenreGroupFilter -> {
                    incGenres.addAll(filter.included)
                    excGenres.addAll(filter.excluded)
                }
                is GenreInModeFilter -> incGenresMode = filter.selected
                is GenreExModeFilter -> excGenresMode = filter.selected
                is YearFilter -> {
                    filter.state.takeIf { it.isNotEmpty() }?.let { year ->
                        if (year.contains("-")) {
                            releaseYearMin = year.substringBefore("-").trim().toIntOrNull()
                            releaseYearMax = year.substringAfter("-").trim().toIntOrNull()
                        } else {
                            val y = year.trim().toIntOrNull()
                            releaseYearMin = y
                            releaseYearMax = y
                        }
                    }
                }
                is OriginalLanguageFilter -> incOLangs = filter.selected
                is TranslationLanguageFilter -> {
                    if (filter.selected.isNotEmpty() && lang == "all") {
                        incTLangs = filter.selected
                    }
                }
                is OriginalStatusFilter -> origStatus = filter.selected
                is UploadStatusFilter -> uploadStatus = filter.selected
                is SortFilter -> sort = filter.selected
                is ChapterCountFilter -> chapCount = filter.selected
                else -> {}
            }
        }

        val variables = ApiComicSearchVariables(
            page = page,
            size = BROWSE_PAGE_SIZE,
            init = (page - 1) * BROWSE_PAGE_SIZE,
            sortby = sort,
            word = query.takeIf { it.isNotEmpty() },
            where = if (letterMode) "letter" else "browse",
            releaseYearMin = releaseYearMin,
            releaseYearMax = releaseYearMax,
            incTypes = types.takeIf { it.isNotEmpty() },
            incDemographics = demographics.takeIf { it.isNotEmpty() },
            incContentRatings = contentRating.takeIf { it.isNotEmpty() },
            incGenres = incGenres.takeIf { it.isNotEmpty() },
            excGenres = excGenres.takeIf { it.isNotEmpty() },
            incGenresMode = incGenresMode?.takeIf { it.isNotEmpty() },
            excGenresMode = excGenresMode?.takeIf { it.isNotEmpty() },
            incOLangs = incOLangs.takeIf { it.isNotEmpty() },
            incTLangs = incTLangs.takeIf { it.isNotEmpty() },
            origStatus = origStatus.takeIf { it.isNotEmpty() },
            siteStatus = uploadStatus.takeIf { it.isNotEmpty() },
            chapCount = chapCount.takeIf { it.isNotEmpty() },
            ignoreGlobalGenres = isIgnoreGenreBlocklist(),
        )

        val response = client.newCall(graphQLRequest(ApiComicSearchWrapper(variables), COMIC_SEARCH_QUERY)).await()
        return parseSearchManga(response)
    }

    private fun parseSearchManga(response: Response): MangasPage {
        val data = response.parseGraphQLAs<SearchData>()
        val mangas = data.items.map { item ->
            item.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
        }
        return MangasPage(mangas, data.pager.hasNextPage())
    }

    // ============================== Filters ==============================
    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = buildList {
            addAll(
                listOf(
                    SortFilter(SortFilter.POPULAR_INDEX),
                    ContentRatingFilter(),
                    TypeFilter(),
                    DemographicFilter(),
                    Filter.Separator(),
                    GenreGroupFilter(),
                    FormatFilter(),
                    GenreInModeFilter(),
                    GenreExModeFilter(),
                    Filter.Separator(),
                    OriginalStatusFilter(),
                    UploadStatusFilter(),
                    OriginalLanguageFilter(),
                ),
            )
            if (lang == "all") {
                add(TranslationLanguageFilter())
            }
            addAll(
                listOf(
                    ChapterCountFilter(),
                    YearFilter(),
                    LetterFilter(),
                ),
            )
        }
        return FilterList(filters)
    }

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) getMangaDetails(manga) else manga
        val chapterList = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(details, chapterList)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val apiVariables = ApiComicNodeVariables(id = getMangaId(manga.url))
        val response = client.newCall(graphQLRequest(apiVariables, COMIC_NODE_QUERY)).await()
        return parseMangaDetails(response)
    }

    private fun parseMangaDetails(response: Response): SManga {
        val result = response.parseGraphQLAs<ComicNodeData>()
        return result.response.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
    }

    override fun getMangaUrl(manga: SManga): String {
        val url = manga.url
        return if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/comic/$url"
    }

    private fun getMangaId(url: String): String {
        val matchResult = urlIdRegex.find(url)
        return matchResult?.groups?.get(1)?.value ?: url
    }

    // ============================= Chapters ==============================
    private suspend fun getChapterList(manga: SManga): List<SChapter> = fetchChapterListPage(manga, 1)

    private suspend fun fetchChapterListPage(manga: SManga, page: Int): List<SChapter> {
        val select = ApiChapterListSelect(
            comicId = getMangaId(manga.url),
            page = page,
            size = 100,
        )
        val response = client.newCall(graphQLRequest(ApiChapterListWrapper(select), CHAPTER_LIST_QUERY)).await()
        val data = response.parseGraphQLAs<ChapterListData>().response
        val chapters = data.items.map { it.data.toSChapter() }

        return if (data.paging.hasNextPage()) {
            chapters + fetchChapterListPage(manga, page + 1)
        } else {
            chapters
        }
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = chapter.url
        val absUrl = if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/comic/chapter/$url"

        val response = client.newCall(GET(absUrl, headers)).await()
        val document = response.asJsoup()

        // Parse images directly from HTML
        var images = document.select("div[data-name=\"image-item\"] img[src]")
        if (images.isNotEmpty()) {
            return images.mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
        }

        images = document.select("img[src^=\"/_f/\"]")
        if (images.isNotEmpty()) {
            return images.mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
        }

        images = document.select("img[src*=\"/_f/\"]")
        if (images.isNotEmpty()) {
            return images.mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
        }

        images = document.select("img[src]")
        if (images.isNotEmpty()) {
            val valid = images.filter { img ->
                val src = img.absUrl("src")
                src.contains("/_f/") || src.contains("/images/") || src.endsWith(".webp") || src.endsWith(".jpg")
            }
            if (valid.isNotEmpty()) {
                return valid.mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
            }
        }

        // Fallback to script parsing if images are encrypted
        val scripts = document.select("script")
        for (script in scripts) {
            val scriptSrc = script.html()
            val p = scriptSrc.indexOf("const imgHttps =")
            if (p == -1) continue

            val start = scriptSrc.indexOf('[', p)
            val end = scriptSrc.indexOf(';', start)
            if (start == -1 || end == -1) continue

            val imagesArray = JSONArray(scriptSrc.substring(start, end))
            val batoPass = scriptSrc.substringAfter("batoPass =").substringBefore(";").trim(' ', '"', '\n')
            val batoWord = scriptSrc.substringAfter("batoWord =").substringBefore(";").trim(' ', '"', '\n')

            val pages = mutableListOf<Page>()
            if (batoPass.isNotEmpty() && batoWord.isNotEmpty()) {
                val args = JSONArray(decryptAES(batoWord, batoPass))
                for (i in 0 until imagesArray.length()) {
                    val imgurl = imagesArray.getString(i)
                    val fullUrl = if (args.length() == 0) imgurl else "$imgurl?${args.getString(i)}"
                    pages.add(Page(i, imageUrl = fullUrl))
                }
            } else {
                for (i in 0 until imagesArray.length()) {
                    pages.add(Page(i, imageUrl = imagesArray.getString(i)))
                }
            }
            return pages
        }

        throw Exception("Cannot find images list")
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url
        return if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/comic/chapter/$url"
    }

    private fun cleanTitleIfNeeded(title: String): String {
        var tempTitle = title
        customRemoveTitle().takeIf { it.isNotEmpty() }?.let { customRegex ->
            runCatching {
                tempTitle = tempTitle.replace(Regex(customRegex), "")
            }
        }
        if (isRemoveTitleVersion()) {
            tempTitle = tempTitle.replace(titleRegex, "")
        }
        return tempTitle.trim()
    }

    private inline fun <reified T : Any> graphQLRequest(variables: T, query: String): Request = graphQLPost("$baseUrl/query/", headers, query, null, variables)

    private fun decryptAES(encrypted: String, password: String): String {
        val cipherData = android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT)
        val saltData = cipherData.copyOfRange(8, 16)
        val (key, iv) = generateKeyAndIV(
            keyLength = 32,
            ivLength = 16,
            iterations = 1,
            salt = saltData,
            password = password.toByteArray(StandardCharsets.UTF_8),
            md = MessageDigest.getInstance("MD5"),
        )
        val encryptedData = cipherData.copyOfRange(16, cipherData.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return String(cipher.doFinal(encryptedData), StandardCharsets.UTF_8)
    }

    private fun generateKeyAndIV(
        keyLength: Int,
        ivLength: Int,
        iterations: Int,
        salt: ByteArray,
        password: ByteArray,
        md: MessageDigest,
    ): Pair<SecretKeySpec, IvParameterSpec> {
        val digestLength = md.digestLength
        val requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0
        md.reset()
        while (generatedLength < keyLength + ivLength) {
            if (generatedLength > 0) {
                md.update(generatedData, generatedLength - digestLength, digestLength)
            }
            md.update(password)
            md.update(salt, 0, 8)
            md.digest(generatedData, generatedLength, digestLength)
            repeat(iterations - 1) {
                md.update(generatedData, generatedLength, digestLength)
                md.digest(generatedData, generatedLength, digestLength)
            }
            generatedLength += digestLength
        }

        return SecretKeySpec(generatedData.copyOfRange(0, keyLength), "AES") to IvParameterSpec(
            if (ivLength > 0) generatedData.copyOfRange(keyLength, keyLength + ivLength) else byteArrayOf(),
        )
    }

    // ============================ Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles.\n" +
                "To update existing entries, enable 'Update library manga titles' in advanced settings and refresh manually."
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = REMOVE_TITLE_CUSTOM_PREF
            title = "Custom regex to be removed from title"
            summary = customRemoveTitle()
            setDefaultValue("")

            val validate = { str: String ->
                runCatching { Regex(str) }
                    .map { true to "" }
                    .getOrElse { false to it.message }
            }

            setOnBindEditTextListener { editText ->
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun afterTextChanged(editable: Editable?) {
                            editable ?: return
                            val text = editable.toString()
                            val valid = validate(text)
                            editText.error = if (!valid.first) valid.second else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                        }
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val (isValid, message) = validate(newValue as String)
                if (isValid) summary = newValue else Toast.makeText(screen.context, message, Toast.LENGTH_LONG).show()
                isValid
            }
        }.also(screen::addPreference)

        CheckBoxPreference(screen.context).apply {
            key = IGNORE_GENRE_BLOCKLIST_PREF
            title = "Ignore webview genre blocklist"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun isRemoveTitleVersion(): Boolean = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun customRemoveTitle(): String = preferences.getString(REMOVE_TITLE_CUSTOM_PREF, "")!!
    private fun isIgnoreGenreBlocklist(): Boolean = preferences.getBoolean(IGNORE_GENRE_BLOCKLIST_PREF, false)

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val IGNORE_GENRE_BLOCKLIST_PREF = "IGNORE_GENRE_BLOCKLIST"

        private val idQueryRegex = Regex("^id\\s*:?\\s*([a-zA-Z0-9]+)\\s*$", RegexOption.IGNORE_CASE)
        private val urlIdRegex = Regex("""/comic/([a-zA-Z0-9]+)""")

        private const val BROWSE_PAGE_SIZE = 36

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
