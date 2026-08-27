package eu.kanade.tachiyomi.extension.all.komga

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.tryParseDateTime
import java.time.format.DateTimeFormatter

private val formatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val formatterDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

fun parseDate(date: String): Long = formatterDate.tryParseDate(date)

fun parseDateTime(date: String) = formatterDateTime.tryParseDateTime(date)

fun PreferenceScreen.addEditTextPreference(
    title: String,
    default: String,
    summary: String,
    getSummary: (String) -> String = { summary },
    dialogMessage: String? = null,
    inputType: Int? = null,
    validate: ((String) -> Boolean)? = null,
    validationMessage: String? = null,
    key: String = title,
    restartRequired: Boolean = false,
    onComplete: (String) -> Unit = {},
) {
    EditTextPreference(context).apply {
        this.key = key
        this.title = title
        this.summary = summary
        this.setDefaultValue(default)
        dialogTitle = title
        this.dialogMessage = dialogMessage

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

        setOnPreferenceChangeListener { _, newValue ->
            try {
                val text = newValue as String
                val result = text.isBlank() || validate?.invoke(text) ?: true

                if (restartRequired && result) {
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                }

                if (result) {
                    onComplete(text)
                    this.summary = getSummary(text)
                }
                result
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }.also(::addPreference)
}
