package eu.kanade.tachiyomi.extension.all.pornpics

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.lib.i18n.Intl

object PornPicsPreferences {

    private const val PS_KEY_ROOT = "PornPics"

    private const val PS_KEY_CATEGORY = "$PS_KEY_ROOT::CATEGORY"

    const val DEFAULT_CATEGORY_OPTION = "/default"

    private fun buildCategoryOption(intl: Intl): Map<String, String> {
        return linkedMapOf(
            intl["config-category-option-default"] to DEFAULT_CATEGORY_OPTION,
            intl["config-category-option-asian"] to "asian",
            intl["config-category-option-chinese"] to "chinese",
            intl["config-category-option-korean"] to "korean",
            intl["config-category-option-japanese"] to "japanese",
            intl["config-category-option-russian"] to "russian",
            intl["config-category-option-ukrainian"] to "ukrainian",
            intl["config-category-option-big-tits"] to "big-tits",
            intl["config-category-option-natural-tits"] to "natural-tits",
            intl["config-category-option-cosplay"] to "cosplay",
            intl["config-category-option-cute"] to "cute",
            intl["config-category-option-glasses"] to "glasses",
            intl["config-category-option-maid"] to "maid",
            intl["config-category-option-nurse"] to "nurse",
            intl["config-category-option-nun"] to "nun",
            intl["config-category-option-stockings"] to "stockings",
            intl["config-category-option-twins"] to "twins",
        )
    }

    fun buildPreferences(content: Context, intl: Intl): List<ListPreference> {
        val options = buildCategoryOption(intl)

        return listOf(
            ListPreference(content).apply {
                key = PS_KEY_CATEGORY
                title = intl["config-category-title"]
                summary = intl["config-category-summary"]
                entries = options.keys.toTypedArray()
                entryValues = options.values.toTypedArray()
            },
        )
    }

    fun getCategoryOption(sharedPreferences: SharedPreferences): String? {
        val option = sharedPreferences.getString(PS_KEY_CATEGORY, DEFAULT_CATEGORY_OPTION)
        return if (DEFAULT_CATEGORY_OPTION == option) DEFAULT_CATEGORY_OPTION else option
    }
}
