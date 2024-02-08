package eu.kanade.tachiyomi.extension.all.komga

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.komga.dto.BookDto
import eu.kanade.tachiyomi.extension.all.komga.dto.PageWrapperDto
import eu.kanade.tachiyomi.extension.all.komga.dto.ReadListDto
import eu.kanade.tachiyomi.extension.all.komga.dto.SeriesDto
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import org.apache.commons.text.StringSubstitutor
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object KomgaUtils {
    private val json: Json by injectLazy()

    val formatterDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val formatterDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val formatterDateTimeMilli = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun parseDate(date: String?): Long = runCatching {
        formatterDate.parse(date!!)!!.time
    }.getOrDefault(0L)

    fun parseDateTime(date: String?) = if (date == null) {
        0L
    } else {
        runCatching {
            formatterDateTime.parse(date)!!.time
        }
            .getOrElse {
                formatterDateTimeMilli.parse(date)?.time ?: 0L
            }
    }

    fun Response.isFromReadList() = request.url.toString().contains("/api/v1/readlists")

    fun processSeriesPage(response: Response, baseUrl: String, urlPrefix: String): MangasPage {
        return if (response.isFromReadList()) {
            val data = response.parseAs<PageWrapperDto<ReadListDto>>()

            MangasPage(data.content.map { it.toSManga(baseUrl, urlPrefix) }, !data.last)
        } else {
            val data = response.parseAs<PageWrapperDto<SeriesDto>>()

            MangasPage(data.content.map { it.toSManga(baseUrl, urlPrefix) }, !data.last)
        }
    }

    fun formatChapterName(book: BookDto, chapterNameTemplate: String, isFromReadList: Boolean): String {
        val values = hashMapOf(
            "title" to book.metadata.title,
            "seriesTitle" to book.seriesTitle,
            "number" to book.metadata.number,
            "createdDate" to book.created,
            "releaseDate" to book.metadata.releaseDate,
            "size" to book.size,
            "sizeBytes" to book.sizeBytes.toString(),
        )
        val sub = StringSubstitutor(values, "{", "}")

        return buildString {
            if (isFromReadList) {
                append(book.seriesTitle)
                append(" ")
            }

            append(sub.replace(chapterNameTemplate))
        }
    }

    fun SeriesDto.toSManga(baseUrl: String, urlPrefix: String): SManga =
        SManga.create().apply {
            title = metadata.title
            url = "$urlPrefix/api/v1/series/$id"
            thumbnail_url = "${url.replaceFirst(urlPrefix, baseUrl)}/thumbnail"
            status = when {
                metadata.status == "ENDED" && metadata.totalBookCount != null && booksCount < metadata.totalBookCount -> SManga.PUBLISHING_FINISHED
                metadata.status == "ENDED" -> SManga.COMPLETED
                metadata.status == "ONGOING" -> SManga.ONGOING
                metadata.status == "ABANDONED" -> SManga.CANCELLED
                metadata.status == "HIATUS" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = (metadata.genres + metadata.tags + booksMetadata.tags).distinct().joinToString(", ")
            description = metadata.summary.ifBlank { booksMetadata.summary }
            booksMetadata.authors.groupBy { it.role }.let { map ->
                author = map["writer"]?.map { it.name }?.distinct()?.joinToString()
                artist = map["penciller"]?.map { it.name }?.distinct()?.joinToString()
            }
        }

    fun ReadListDto.toSManga(baseUrl: String, urlPrefix: String): SManga =
        SManga.create().apply {
            title = name
            description = summary
            url = "$urlPrefix/api/v1/readlists/$id"
            thumbnail_url = "${url.replaceFirst(urlPrefix, baseUrl)}/thumbnail"
            status = SManga.UNKNOWN
        }

    fun PreferenceScreen.addEditTextPreference(
        title: String,
        default: String,
        summary: String,
        inputType: Int? = null,
        validate: ((String) -> Boolean)? = null,
        validationMessage: String? = null,
        key: String = title,
    ) {
        EditTextPreference(context).apply {
            this.key = key
            this.title = title
            this.summary = summary
            this.setDefaultValue(default)
            dialogTitle = title

            setOnBindEditTextListener { editText ->
                if (inputType != null) {
                    editText.inputType = inputType
                }

                if (validate != null) {
                    editText.addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(editable: Editable?) {
                                requireNotNull(editable)

                                val text = editable.toString()

                                val isValid = text.isBlank() || validate(text)

                                editText.error = if (!isValid) validationMessage else null
                                editText.rootView.findViewById<Button>(android.R.id.button1)
                                    ?.isEnabled = editText.error == null
                            }
                        },
                    )
                }
            }

            setOnPreferenceChangeListener { _, _ ->
                try {
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    text.isBlank() || validate?.invoke(text) ?: true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.also(::addPreference)
    }

    inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
}
