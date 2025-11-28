package eu.kanade.tachiyomi.extension.zh.bakamh

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import okhttp3.HttpUrl.Companion.toHttpUrl

object BakamhPreferences {

    private const val PS_KEY_ROOT = "BAKAMH"
    private const val PS_KEY_DOMAIN = "$PS_KEY_ROOT::DOMAIN"
    private const val PS_KEY_DOMAIN_DEFAULT = "$PS_KEY_DOMAIN::DEFAULT"
    private const val PS_KEY_DOMAIN_OVERRIDE = "$PS_KEY_DOMAIN::OVERRIDE"

    private const val PS_KEY_USE_DEFAULT_SEC_CH_UA = "$PS_KEY_ROOT::USE_DEFAULT_SEC_CH_UA"

    private const val PS_KEY_CUSTOM_SEC_CH_UA = "$PS_KEY_ROOT::CUSTOM_SEC_CH_UA"

    private const val PS_KEY_CUSTOM_SEC_CH_UA_DEFAULT = "$PS_KEY_CUSTOM_SEC_CH_UA::DEFAULT"
    private const val PS_KEY_CUSTOM_SEC_CH_UA_OVERRIDE = "$PS_KEY_CUSTOM_SEC_CH_UA::OVERRIDE"

    const val DEFAULT_DOMAIN = "https://bakamh.com"

    private const val DEFAULT_SEC_CH_UA =
        """"Chromium";v="142", "Microsoft Edge";v="142", "Not_A Brand";v="99""""

    // "<brand>";v="<version>"
    // "Chromium";v="120", "Not.A?Brand";v="99"
    private val SEC_CH_UA_PATTERN = Regex(""""[^"]+";v="\d+(\.\d+)"""")

    internal fun SharedPreferences.preferenceMigration() {
        // refresh when DEFAULT_DOMAIN update
        refreshDefaultValue(PS_KEY_DOMAIN_DEFAULT, PS_KEY_DOMAIN_OVERRIDE, DEFAULT_DOMAIN)
        // refresh when custom Sec-CH-UA update
        refreshDefaultValue(
            PS_KEY_CUSTOM_SEC_CH_UA_DEFAULT,
            PS_KEY_CUSTOM_SEC_CH_UA_OVERRIDE,
            DEFAULT_SEC_CH_UA,
        )
    }

    private fun SharedPreferences.refreshDefaultValue(
        key: String,
        overrideKey: String,
        defaultValue: String,
    ) {
        val current = getString(key, defaultValue)!!
        if (current != defaultValue) {
            edit()
                .putString(key, defaultValue)
                .putString(overrideKey, defaultValue)
                .apply()
        }
    }

    internal fun SharedPreferences.baseUrl(): String {
        val httpUrl = getString(PS_KEY_DOMAIN_OVERRIDE, DEFAULT_DOMAIN)!!.toHttpUrl()

        return httpUrl.newBuilder()
            .scheme("https")
            .host(httpUrl.host)
            .build()
            .toString()
            .removeSuffix("/")
    }

    internal fun SharedPreferences.secChUa(): String {
        val useDefault = getBoolean(PS_KEY_USE_DEFAULT_SEC_CH_UA, true)
        if (useDefault) {
            return DEFAULT_SEC_CH_UA
        }

        return getString(PS_KEY_CUSTOM_SEC_CH_UA_OVERRIDE, DEFAULT_SEC_CH_UA)!!
    }

    internal fun buildPreferences(context: Context): List<Preference> {
        return listOf(
            buildDomainPreference(context),
            buildUseDefaultSecChUaPreference(context),
            buildCustomSecChUaPreference(context),
        )
    }

    internal fun buildDomainPreference(context: Context): Preference {
        return EditTextPreference(context).apply {
            key = PS_KEY_DOMAIN_OVERRIDE
            title = "域名设置"
            summary = "如果当前域名无法访问，可在此处设置，重启应用后生效。"
            dialogTitle = title
            dialogMessage = "域名可在发布页获取，默认域名为 $DEFAULT_DOMAIN"

            setDefaultValue(DEFAULT_DOMAIN)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    (newValue as String).toHttpUrl()
                } catch (_: IllegalArgumentException) {
                    Toast.makeText(
                        context,
                        "设置域名失败！",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@setOnPreferenceChangeListener false
                }

                Toast.makeText(
                    context,
                    "域名设置为 $newValue ！重启应用后生效。",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }
    }

    internal fun buildUseDefaultSecChUaPreference(context: Context): SwitchPreferenceCompat {
        return SwitchPreferenceCompat(context).apply {
            key = PS_KEY_USE_DEFAULT_SEC_CH_UA
            title = "使用默认的 Sec-CH-UA"
            summaryOn = "使用默认的 Sec-CH-UA"
            summaryOff = "使用自定义的 Sec-CH-UA"
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val message = if (newValue as Boolean) {
                    "已设置使用默认的 Sec-CH-UA，重启应用后生效。"
                } else {
                    "已设置使用自定义的 Sec-CH-UA，重启应用后生效。"
                }
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }
    }

    private fun isValidSecChUaFormat(secChUa: String): Boolean {
        if (secChUa.isBlank()) {
            return false
        }
        val uas = secChUa.split(",")
        for (ua in uas) {
            val trimmedUa = ua.trim()
            if (!SEC_CH_UA_PATTERN.matches(trimmedUa)) {
                return false
            }
        }
        return true
    }

    internal fun buildCustomSecChUaPreference(context: Context): EditTextPreference {
        return EditTextPreference(context).apply {
            key = PS_KEY_CUSTOM_SEC_CH_UA_OVERRIDE
            title = "自定义Sec-CH-UA"
            summary = "自定义的Sec-CH-UA，关闭使用默认的Sec-CH-UA生效。"
            dialogTitle = title
            dialogMessage = summary

            setDefaultValue(DEFAULT_SEC_CH_UA)

            setOnPreferenceChangeListener { _, newValue ->
                // 检测设置的值是否合法
                if (!isValidSecChUaFormat(newValue as String)) {
                    Toast.makeText(
                        context,
                        "设置自定义Sec-CH-UA失败！请检查格式是否正确。",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@setOnPreferenceChangeListener false
                }

                Toast.makeText(
                    context,
                    "设置自定义Sec-CH-UA成功 ！重启应用后生效。",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }
    }
}
