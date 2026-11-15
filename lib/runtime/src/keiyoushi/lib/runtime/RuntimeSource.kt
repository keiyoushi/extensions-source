package keiyoushi.lib.runtime

import android.app.AlertDialog
import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import java.io.IOException

/**
 * The Keiyoushi Runtime's one source, there for its settings: the runtime's version, its modules,
 * and the licenses of the libraries it bundles. It isn't a `CatalogueSource`, so it has no
 * language and the apps leave it out of Browse and global search; the extension's details page
 * still shows its settings.
 *
 * @param licenses library name to the license file's path inside the runtime's APK or JAR
 */
class RuntimeSource(
    override val id: Long,
    override val name: String,
    private val versionName: String,
    private val apis: Map<String, Int>,
    private val licenses: Map<String, String>,
) : Source,
    ConfigurableSource {
    override val supportsLatest = false

    // The app titles a source's settings screen with source.toString(); without this it shows the
    // obfuscated class toString() (e.g. "a1@8050f37").
    override fun toString(): String = name

    override suspend fun getPopularManga(page: Int): MangasPage = throw noManga()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw noManga()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = throw noManga()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw noManga()

    override suspend fun getPageList(chapter: SChapter): List<Page> = throw noManga()

    private fun noManga() = IOException("$name has no manga: it provides shared code for other extensions.")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(screen.info("Version", versionName))
        apis.toSortedMap().forEach { (module, api) -> screen.addPreference(screen.info(module, "API $api")) }
        licenses.toSortedMap().forEach { (library, path) ->
            screen.addPreference(
                screen.info("$library license", "Tap to read").apply {
                    setOnPreferenceClickListener {
                        AlertDialog.Builder(screen.context)
                            .setTitle(library)
                            .setMessage(readLicense(path))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        true
                    }
                },
            )
        }
    }

    private fun PreferenceScreen.info(title: String, summary: String) = newPreference(context).apply {
        key = "runtime_$title"
        this.title = title
        this.summary = summary
    }

    // The extensions-lib compile stub lacks Preference's Context constructor, so build it reflectively against the app's class.
    private fun newPreference(context: Context): Preference = Preference::class.java.getConstructor(Context::class.java).newInstance(context)

    // The runtime's own class loader reads from its APK or JAR
    private fun readLicense(path: String): String = javaClass.classLoader?.getResourceAsStream(path)
        ?.bufferedReader()?.use { it.readText() }
        ?: "Missing $path"
}
