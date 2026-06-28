package eu.kanade.tachiyomi.extension.ar.procomic

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class UrlActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val activityView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        // Title
        val title = TextView(this).apply {
            text = "إعدادات Procomic"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }
        activityView.addView(title)

        // Get preferences
        preferences = getSharedPreferences("source_procomic_prefs", 0)

        // Custom URL Section
        val urlLabel = TextView(this).apply {
            text = "تغيير رابط الموقع"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        activityView.addView(urlLabel)

        val urlButton = Button(this).apply {
            text = "تعديل الرابط"
            setOnClickListener { showUrlDialog() }
        }
        activityView.addView(urlButton)

        // Hide Paid Chapters Switch
        val paidLabel = TextView(this).apply {
            text = "إخفاء الفصول المدفوعة"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        activityView.addView(paidLabel)

        val paidSwitch = Switch(this).apply {
            isChecked = preferences.getBoolean("hide_paid_chapters", true)
            setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean("hide_paid_chapters", isChecked).apply()
                Toast.makeText(
                    this@UrlActivity,
                    if (isChecked) "الفصول المدفوعة مخفية" else "الفصول المدفوعة مرئية",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        activityView.addView(paidSwitch)

        // Safe Browsing Switch
        val safeLabel = TextView(this).apply {
            text = "التصفح الآمن (Safe Browsing)"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        activityView.addView(safeLabel)

        val safeSwitch = Switch(this).apply {
            isChecked = preferences.getBoolean("safe_browsing", true)
            setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean("safe_browsing", isChecked).apply()
                Toast.makeText(
                    this@UrlActivity,
                    if (isChecked) "التصفح الآمن مفعل" else "التصفح الآمن معطل",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        activityView.addView(safeSwitch)

        // Reset Button
        val resetButton = Button(this).apply {
            text = "إعادة تعيين الإعدادات"
            setOnClickListener { resetSettings() }
        }
        activityView.addView(resetButton)

        setContentView(activityView)
    }

    private fun showUrlDialog() {
        val currentUrl = preferences.getString("custom_base_url", "https://procomic.pro") ?: "https://procomic.pro"
        
        val editText = EditText(this).apply {
            setText(currentUrl)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            hint = "أدخل الرابط الجديد"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("تعديل رابط الموقع")
            .setView(editText)
            .setPositiveButton("حفظ") { _, _ ->
                val newUrl = editText.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    preferences.edit().putString("custom_base_url", newUrl).apply()
                    Toast.makeText(this, "تم حفظ الرابط: $newUrl", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "الرابط فارغ!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .setNeutralButton("إعادة تعيين") { _, _ ->
                preferences.edit().putString("custom_base_url", "https://procomic.pro").apply()
                Toast.makeText(this, "تم إعادة تعيين الرابط الافتراضي", Toast.LENGTH_SHORT).show()
            }
            .create()

        dialog.show()
    }

    private fun resetSettings() {
        AlertDialog.Builder(this)
            .setTitle("تأكيد")
            .setMessage("هل تريد إعادة تعيين جميع الإعدادات؟")
            .setPositiveButton("نعم") { _, _ ->
                preferences.edit().clear().apply()
                Toast.makeText(this, "تم إعادة تعيين الإعدادات", Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton("لا", null)
            .create()
            .show()
    }
}
