package eu.kanade.tachiyomi.extension.zh.hanabimanga

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat

const val PREF_EMAIL = "EMAIL"
const val PREF_PASSWORD = "PASSWORD"
const val PREF_POPULAR_MANGA = "POPULAR_MANGA"
const val PREF_AI_SR = "AI_SUPER_RESOLUTION"

private const val LOGIN_ADVICE = "未登录（部分章节需要登录后才能阅读）"
private const val LOGIN_DIALOG = "使用花火漫画账号登录，登录后才能阅读需要权限的章节"

/** The badge beside the login item: a live session, a recorded failure, or the plain advice. */
enum class LoginStatus { PENDING, ACTIVE, FAILED, NONE }

fun loginSummary(pref: SharedPreferences, status: LoginStatus, error: String?): String {
    val account = pref.getString(PREF_EMAIL, null)?.takeIf(String::isNotBlank) ?: return LOGIN_ADVICE
    return when (status) {
        LoginStatus.PENDING -> "登录中......"
        LoginStatus.ACTIVE -> "已登录：$account"
        LoginStatus.FAILED -> "登录失败：" + (error?.takeIf(String::isNotBlank) ?: "未知错误")
        LoginStatus.NONE -> LOGIN_ADVICE
    }
}

/** The compile stub lacks the Context constructor the real androidx class has had forever. */
private fun plainPreference(context: Context): Preference = Preference::class.java.getConstructor(Context::class.java).newInstance(context)

/** One dialog for both fields, so a credential pair is only ever saved as a pair. */
private fun showLoginDialog(context: Context, pref: SharedPreferences, onSaved: () -> Unit) {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    val account = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT
        hint = "邮箱"
        setText(pref.getString(PREF_EMAIL, "") ?: "")
    }
    val password = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = "登录密码"
        setText(pref.getString(PREF_PASSWORD, "") ?: "")
    }
    val box = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(account)
        addView(password)
    }

    val dialog = android.app.AlertDialog.Builder(context)
        .setTitle("登录账号")
        .setMessage(LOGIN_DIALOG)
        .setView(box)
        .setPositiveButton("登录", null)
        .setNegativeButton("退出账号", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val user = account.text.toString().trim()
            val pass = password.text.toString()
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(context, "账号和密码都不能为空", Toast.LENGTH_SHORT).show()
            } else {
                pref.edit().putString(PREF_EMAIL, user).putString(PREF_PASSWORD, pass).apply()
                dialog.dismiss()
                onSaved()
            }
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            pref.edit().remove(PREF_EMAIL).remove(PREF_PASSWORD).apply()
            dialog.dismiss()
            onSaved()
        }
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
    account.requestFocus()
}

fun preferencesInternal(
    context: Context,
    pref: SharedPreferences,
    onCredentialsChanged: () -> Unit,
    loginStatus: () -> LoginStatus,
    loginError: () -> String?,
): Array<Preference> {
    lateinit var login: Preference
    login = plainPreference(context).apply {
        key = PREF_EMAIL
        title = "登录账号"
        summary = loginSummary(pref, loginStatus(), loginError())
        setOnPreferenceClickListener {
            showLoginDialog(context, pref) {
                onCredentialsChanged()
                login.summary = loginSummary(pref, loginStatus(), loginError())
            }
            true
        }
    }
    return arrayOf(
        login,
        ListPreference(context).apply {
            key = PREF_POPULAR_MANGA
            title = "热门漫画显示"
            summary = "%s"
            entries = arrayOf("日榜", "周榜", "月榜", "评分", "人气")
            entryValues = arrayOf(
                "popularity_daily.desc.nullslast",
                "popularity_weekly.desc.nullslast",
                "popularity_monthly.desc.nullslast",
                "rating_average.desc.nullslast",
                "rating_count.desc.nullslast",
            )
            setDefaultValue("popularity_daily.desc.nullslast")
        },
        SwitchPreferenceCompat(context).apply {
            key = PREF_AI_SR
            title = "AI 超分辨率"
            summary = "尝试获取超分辨率图片，普通用户每日限 5 个章节。不建议非 VIP 用户开启，避免额度用完后还要再关"
            setDefaultValue(false)
        },
    )
}
