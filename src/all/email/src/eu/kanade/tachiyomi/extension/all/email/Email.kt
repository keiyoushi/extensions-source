package eu.kanade.tachiyomi.extension.all.email

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.text.InputType
import android.widget.Toast
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.apache.commons.text.WordUtils
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Folder
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import androidx.preference.CheckBoxPreference as XCheckBoxPreference

class Email : ConfigurableSource, HttpSource() {

    override val name = "Email"
    override val supportsLatest = false

    override val lang = "all"
    override val baseUrl = "https://icanhazip.com"

    private val mail by lazy { getPrefMail() }
    private val pass by lazy { getPrefPass() }
    private val host by lazy { getPrefHost() }
    private val port by lazy { getPrefPort() }
    private val ssl by lazy { getPrefSSL() }

    private fun getPrefMail(): String = preferences.getString(SETTING_MAIL, DEFAULT_MAIL)!!
    private fun getPrefPass(): String = preferences.getString(SETTING_PASS, DEFAULT_PASS)!!
    private fun getPrefHost(): String = preferences.getString(SETTING_HOST, DEFAULT_HOST)!!
    private fun getPrefPort(): Int = try { preferences.getString(SETTING_PORT, DEFAULT_PORT.toString())!!.toInt() } catch (e: java.lang.Exception) { 993 }
    private fun getPrefSSL(): Boolean = preferences.getBoolean(SETTING_SSL, DEFAULT_SSL)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun getText(p: Part): String? {
        if (p.isMimeType("text/*")) {
            val s = p.content as String
            return if (p.isMimeType("text/html")) {
                Jsoup.parse(s).text()
            } else { s }
        }
        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            val mp: Multipart = p.getContent() as Multipart
            var text: String? = null
            for (i in 0 until mp.getCount()) {
                val bp: Part = mp.getBodyPart(i)
                if (bp.isMimeType("text/plain")) {
                    if (text == null) text = getText(bp)
                    continue
                } else if (bp.isMimeType("text/html")) {
                    val s = getText(bp)
                    if (s != null) return Jsoup.parse(s).text()
                } else {
                    return getText(bp)
                }
            }
            return text
        } else if (p.isMimeType("multipart/*")) {
            val mp: Multipart = p.getContent() as Multipart
            for (i in 0 until mp.getCount()) {
                val s = getText(mp.getBodyPart(i))
                if (s != null) return s
            }
        }
        return "Empty Email"
    }

    @SuppressLint("SetJavaScriptEnabled", "NewApi")
    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val request = chain.request()
        val url = request.url().toString()
        val newRequest = request.newBuilder()
            .url(url.substringBeforeLast("/"))
            .build()
        val response = chain.proceed(newRequest)
        if (!url.contains("icanhaz")) return@addInterceptor response

        // email to image code
        val number = url.substringAfterLast("/").toInt()

        val session = Session.getDefaultInstance(
            Properties().apply {
                put("mail.imap.ssl.enable", if (ssl) { "true" } else { "false" })
            }
        )
        val store = session.getStore("imap")
        store.connect(host, port, mail, pass)
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)

        val message = getText(inbox.getMessage(number))

        // thanks to https://github.com/tachiyomiorg/tachiyomi-extensions/pull/3506/commits/80c238123551e898d7fcf1233bd9696516eedc99
        var flavourTextParagraphs = message!!.split("\n")

        if (flavourTextParagraphs.isEmpty())
            flavourTextParagraphs = listOf("No flavour text for the previous page.")

        val paint = Paint().apply {
            textAlign = Paint.Align.CENTER
            color = Color.parseColor("#eeeeee")
            isAntiAlias = true
            textSize = 42f
        }
        val lineSpacing = paint.descent() - paint.ascent()

        val stringBuilder = StringBuilder()

        for (paragraph in flavourTextParagraphs) {
            val p = WordUtils.wrap(paragraph, 50, "\n", true)
            stringBuilder.append(p)
            stringBuilder.append("\n\n")
        }
        val linesToDraw = stringBuilder.split("\n")

        val imageHeight = imageVerticalMargin * 2 + linesToDraw.size * lineSpacing

        val bitmap = Bitmap.createBitmap(imageWidth, imageHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val x = (imageWidth / 2).toFloat()
        var y = imageVerticalMargin.toFloat()
        canvas.save()
        canvas.restore()
        for (line in linesToDraw) {
            canvas.drawText(line, x, y, paint)
            y += lineSpacing
        }

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)

        val rb = ResponseBody.create(MediaType.parse("image/png"), byteArrayOutputStream.toByteArray())
        byteArrayOutputStream.close()
        response.newBuilder().body(rb).build()
    }.build()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("/archive")
        manga.title = "Email"
        manga.artist = "az4521"
        manga.author = "az4521"
        manga.status = SManga.ONGOING
        manga.description = arrayOf("Tachiyomi Email Client", "${getPrefHost()}:${getPrefPort()}", getPrefMail()).joinToString("\n")
        manga.thumbnail_url = thumbnailUrl
        manga.genre = "webcomic, webtoon, long strip, email"

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1)
        .map { it.mangas.first().apply { initialized = true } }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val session = Session.getDefaultInstance(
            Properties().apply {
                put("mail.imap.ssl.enable", if (ssl) { "true" } else { "false" })
            }
        )
        val store = session.getStore("imap")
        store.connect(host, port, mail, pass)
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        val chapters = inbox.getMessages(inbox.messageCount - 50, inbox.messageCount).map {
            SChapter.create().apply {
                chapter_number = it.messageNumber.toFloat()
                name = it.subject
                scanlator = it.from.joinToString(", ")
                url = "$baseUrl/${it.messageNumber}"
                date_upload = it.receivedDate.time
            }
        }.reversed()
        return Observable.just(chapters)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(listOf(Page(0, chapter.url, chapter.url)))
    }
    // Prefs
    private fun androidx.preference.PreferenceScreen.editTextPreference(title: String, default: String, value: String, isPassword: Boolean = false, isNumber: Boolean = false): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            if (isPassword) {
                summary = ""
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
            if (isNumber && !isPassword) {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }
    private fun PreferenceScreen.supportEditTextPreference(title: String, default: String, value: String): EditTextPreference {
        return EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(SETTING_HOST, DEFAULT_HOST, host))
        screen.addPreference(screen.editTextPreference(SETTING_PORT, DEFAULT_PORT.toString(), port.toString(), isNumber = true))
        screen.addPreference(
            XCheckBoxPreference(screen.context).apply {
                key = SETTING_SSL
                title = SETTING_SSL
                setDefaultValue(DEFAULT_SSL)

                setOnPreferenceChangeListener { _, newValue ->
                    preferences.edit().putBoolean(key, newValue as Boolean).commit()
                }
            }
        )

        screen.addPreference(screen.editTextPreference(SETTING_MAIL, DEFAULT_MAIL, mail))
        screen.addPreference(screen.editTextPreference(SETTING_PASS, DEFAULT_PASS, pass, isPassword = true))
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(screen.supportEditTextPreference(SETTING_HOST, DEFAULT_HOST, host))
        screen.addPreference(screen.supportEditTextPreference(SETTING_PORT, DEFAULT_PORT.toString(), port.toString()))
        screen.addPreference(
            CheckBoxPreference(screen.context).apply {
                key = SETTING_SSL
                title = SETTING_SSL
                setDefaultValue(DEFAULT_SSL)

                setOnPreferenceChangeListener { _, newValue ->
                    preferences.edit().putBoolean(key, newValue as Boolean).commit()
                }
            }
        )

        screen.addPreference(screen.supportEditTextPreference(SETTING_MAIL, DEFAULT_MAIL, mail))
        screen.addPreference(screen.supportEditTextPreference(SETTING_PASS, DEFAULT_PASS, pass))
    }

    companion object {
        private const val thumbnailUrl = "https://fakeimg.pl/550x780/ffffff/6E7B91/?text=Email&font=museo"
        private const val SETTING_MAIL = "Email Address"
        private const val SETTING_PASS = "Password"
        private const val SETTING_HOST = "IMAP Host"
        private const val SETTING_PORT = "IMAP Port"
        private const val SETTING_SSL = "Use SSL"

        private const val DEFAULT_MAIL = ""
        private const val DEFAULT_PASS = ""
        private const val DEFAULT_HOST = "imap.gmail.com"
        private const val DEFAULT_PORT = 993
        private const val DEFAULT_SSL = true

        const val imageWidth = 1500
        const val imageVerticalMargin = 200
    }

    // unused
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun imageUrlParse(response: Response) = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not Used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")
}
