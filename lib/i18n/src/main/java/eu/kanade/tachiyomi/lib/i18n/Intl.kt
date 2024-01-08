package eu.kanade.tachiyomi.lib.i18n

import org.jetbrains.annotations.PropertyKey
import java.io.InputStreamReader
import java.text.Collator
import java.util.Locale
import java.util.PropertyResourceBundle

/**
 * A simple wrapper to make internationalization easier to use in sources.
 *
 * Message files should be put in the `assets/i18n` folder, with the name
 * `messages_{iso_639_1}.properties`, where `iso_639_1` should be using
 * snake case and be in lowercase.
 *
 * To edit the strings, use the official JetBrain's
 * [Resource Bundle Editor plugin](https://plugins.jetbrains.com/plugin/17035-resource-bundle-editor).
 *
 * Make sure to configure Android Studio to save Properties files as UTF-8 as well.
 * You can refer to this [documentation](https://www.jetbrains.com/help/idea/properties-files.html#1cbc434e)
 * on how to do so.
 */
class Intl(
    language: String,
    availableLanguages: Set<String>,
    private val baseLanguage: String,
    private val classLoader: ClassLoader,
    private val createMessageFileName: (String) -> String = { createDefaultMessageFileName(it) }
) {

    val chosenLanguage: String = when (language) {
        in availableLanguages -> language
        else -> baseLanguage
    }

    private val locale: Locale = Locale.forLanguageTag(chosenLanguage)

    val collator: Collator = Collator.getInstance(locale)

    private val baseBundle: PropertyResourceBundle by lazy { createBundle(baseLanguage) }

    private val bundle: PropertyResourceBundle by lazy {
        if (chosenLanguage == baseLanguage) baseBundle else createBundle(chosenLanguage)
    }

    /**
     * Returns the string from the message file. If the [key] is not present
     * in the current language, the English value will be returned. If the [key]
     * is also not present in English, the [key] surrounded by brackets will be returned.
     */
    @Suppress("InvalidBundleOrProperty")
    operator fun get(@PropertyKey(resourceBundle = "i18n.messages") key: String): String = when {
        bundle.containsKey(key) -> bundle.getString(key)
        baseBundle.containsKey(key) -> baseBundle.getString(key)
        else -> "[$key]"
    }

    /**
     * Uses the string as a format string and returns a string obtained by
     * substituting the specified arguments, using the instance locale.
     */
    @Suppress("InvalidBundleOrProperty")
    fun format(@PropertyKey(resourceBundle = "i18n.messages") key: String, vararg args: Any?) =
        get(key).format(locale, *args)

    fun languageDisplayName(localeCode: String): String =
        Locale.forLanguageTag(localeCode)
            .getDisplayName(locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    /**
     * Creates a [PropertyResourceBundle] instance from the language specified.
     * The expected message file will be loaded from the `res/raw`.
     *
     * The [PropertyResourceBundle] is used directly instead of [java.util.ResourceBundle]
     * because the later has issues with UTF-8 files in Java 8, which would need
     * the message files to be saved in ISO-8859-1, making the file readability bad.
     */
    private fun createBundle(lang: String): PropertyResourceBundle {
        val fileName = createMessageFileName(lang)
        val fileContent = classLoader.getResourceAsStream(fileName)

        return PropertyResourceBundle(InputStreamReader(fileContent, "UTF-8"))
    }

    companion object {
        fun createDefaultMessageFileName(lang: String): String {
            val langSnakeCase = lang.replace("-", "_").lowercase()

            return "assets/i18n/messages_$langSnakeCase.properties"
        }
    }
}
