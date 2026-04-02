package eu.kanade.tachiyomi.extension.ar.mangapro

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ProComic : HttpSource(), ConfigurableSource {
    override val name = "ProComic"
    override val lang = "ar"
    override val supportsLatest = true
    override val versionId = 7

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    // 1. إمكانية تغيير الرابط يدوياً
    override val baseUrl: String
        get() = preferences.getString(BASE_URL_PREF, "https://procomic.pro")!!.removeSuffix("/")

    // 2. حل مشكلة 403 (User-Agent حديث و CookieInterceptor ديناميكي)
    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::scrambledImageInterceptor)
        .addNetworkInterceptor(
            CookieInterceptor(
                baseUrl.toHttpUrl().host,
                listOf("safe_browsing" to "off", "language" to "ar")
            )
        )
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    // --- منطق جلب الفصول مع إخفاء المدفوع ---
    override fun chapterListParse(response: Response): List<SChapter> {
        val hidePaid = preferences.getBoolean(HIDE_PAID_PREF, false)
        val data = response.parseAs<ChapterListData>() // تأكد من وجود كلاس ChapterListData في مشروعك
        
        return data.chapters
            .filter { chapter ->
                // 3. إخفاء الفصول التي سعرها أكبر من 0 (مدفوعة)
                if (hidePaid) chapter.price == 0 else true
            }
            .map { chapter ->
                SChapter.create().apply {
                    url = "/chapter/${chapter.id}"
                    name = "الفصل ${chapter.chapterNumber}"
                    date_upload = parseDate(chapter.createdAt)
                }
            }
    }

    // --- واجهة الإعدادات ---
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "رابط الموقع"
            summary = "الرابط الحالي: $baseUrl"
            setDefaultValue("https://procomic.pro")
            dialogTitle = "تغيير الرابط"
            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PAID_PREF
            title = "إخفاء الفصول المدفوعة"
            summary = "تصفية الفصول التي تتطلب دفع كوينز"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val HIDE_PAID_PREF = "hidePaidChapters"
    }

    // ملاحظة: تأكد من تحديث دوال searchMangaRequest و fetchSearchManga لتستخدم baseUrl الجديد
}
