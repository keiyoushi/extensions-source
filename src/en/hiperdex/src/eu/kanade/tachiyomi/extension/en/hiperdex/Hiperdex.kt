package eu.kanade.tachiyomi.extension.en.hiperdex

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Hiperdex : Hiper() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .set("x-cfg-auth", "yceqt7qgu004")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addHiperAuthInterceptor()
        .rateLimit(3)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)

        val noRemoveTitleBrowsingPref = CheckBoxPreference(screen.context).apply {
            key = NO_REMOVE_TITLE_BROWSING_PREF
            title = "Don't apply title cleaning in browsing/search results"
            summary = "Don't apply the 2 options above when browsing or searching for manga, but still apply them in manga details."
            setVisible(isRemoveTitleVersion() || customRemoveTitle().isNotEmpty())
            setDefaultValue(false)
        }

        CheckBoxPreference(screen.context).apply {
            key = "${REMOVE_TITLE_VERSION_PREF}_$lang"
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Uncensored)' from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                noRemoveTitleBrowsingPref.setVisible(enabled || customRemoveTitle().isNotEmpty())
                true
            }
        }.also { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = "${REMOVE_TITLE_CUSTOM_PREF}_$lang"
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
                if (isValid) {
                    summary = newValue
                    noRemoveTitleBrowsingPref.setVisible(isRemoveTitleVersion() || newValue.isNotEmpty())
                } else {
                    Toast.makeText(screen.context, message, Toast.LENGTH_LONG).show()
                }
                isValid
            }
        }.also { screen.addPreference(it) }

        screen.addPreference(noRemoveTitleBrowsingPref)
    }

    override fun parseSearchMangaList(response: Response): MangasPage {
        val mangaUpdate = super.parseSearchMangaList(response)
        return MangasPage(
            mangaUpdate.mangas.map {
                if (!noCleanTitlesWhileBrowsing()) {
                    it.title = it.title.cleanTitleIfNeeded()
                }
                it
            },
            mangaUpdate.hasNextPage,
        )
    }

    override fun parseMangaDetails(response: Response): SManga = super.parseMangaDetails(response).apply {
        val cleanedTitle = title.cleanTitleIfNeeded()
        if (cleanedTitle != title.trim()) {
            description = listOfNotNull(title, description)
                .joinToString("\n\n")
            title = cleanedTitle
        }
    }

    private fun String.cleanTitleIfNeeded(): String {
        var tempTitle = this
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

    private fun isRemoveTitleVersion(): Boolean = preferences.getBoolean("${REMOVE_TITLE_VERSION_PREF}_$lang", false)
    private fun customRemoveTitle(): String = preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "")!!

    private fun noCleanTitlesWhileBrowsing(): Boolean = preferences.getBoolean(NO_REMOVE_TITLE_BROWSING_PREF, false)

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val NO_REMOVE_TITLE_BROWSING_PREF = "NO_REMOVE_TITLE_BROWSING"

        private val titleRegex: Regex by lazy {
            Regex(
                """^(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩)\s*)+|(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/\s*Official)\s*)+$""",
                RegexOption.IGNORE_CASE,
            )
        }
    }

    override val genresList = listOf(
        "4-Koma",
        "Action",
        "Adaptation",
        "Adult",
        "Adventure",
        "Age Gap",
        "Aliens",
        "Ancient Korea",
        "Anthology",
        "Campus",
        "Childhood Friends",
        "Comedy",
        "Cooking",
        "Crime",
        "Crossdressing",
        "Dance",
        "Delinquents",
        "Demons",
        "Doujinshi",
        "Drama",
        "Ecchi",
        "Escolar",
        "Fantasy",
        "Fellatio/Blowjob",
        "Fetish",
        "Full Color",
        "Furry",
        "Gender Bender",
        "Genderswap",
        "Ghosts",
        "Girls' Love",
        "Gore",
        "Guideverse",
        "Gyaru",
        "Hair Color Change",
        "Harem",
        "Hentai",
        "Heroes",
        "Historical",
        "Horror",
        "Human-Nonhuman Relationship",
        "Isekai",
        "Josei",
        "Korea",
        "Korean Ambience",
        "Korean BL",
        "Long Strip",
        "Long-Haired Male Character/s",
        "Long-Haired Male Lead",
        "Love Triangle/s",
        "Low Fantasy",
        "Maduro",
        "Mafia",
        "Magic",
        "Male Protagonist",
        "Manga",
        "Martial Arts",
        "Masculine Uke",
        "Mature",
        "Mecha",
        "Medical",
        "Military",
        "Monster Girls",
        "Monsters",
        "Monsters Invade Earth",
        "Murim",
        "Muscular Male Lead",
        "Muscular Uke",
        "Music",
        "Mystery",
        "Nameverse",
        "Ninja",
        "Office Workers",
        "Older Uke Younger Seme",
        "Oneshot",
        "Orphan Female Lead",
        "Police",
        "Post-Apocalyptic",
        "Psychological",
        "Red-Haired Male Lead",
        "Red-Haired Seme",
        "Regression",
        "Reincarnation",
        "Revenge",
        "Romance",
        "Samurai",
        "School Life",
        "Sci-fi",
        "Secret Relationship",
        "Seinen",
        "Sexual Violence",
        "Shota",
        "Shoujo",
        "Shoujo Ai",
        "Shounen",
        "Size Difference",
        "Slice of Life",
        "Smut",
        "Sobrenatural",
        "Sports",
        "Superhero",
        "Supernatural",
        "Survival",
        "Suspense",
        "Thriller",
        "Time Travel",
        "Tower",
        "Tragedy",
        "Uncensored",
        "Video Games",
        "Villainess",
        "Violence",
        "Virtual Reality",
        "Web Comic",
        "Webtoon",
        "Wuxia",
        "Yaoi",
        "Yuri",
    )
}
