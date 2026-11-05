@file:Suppress("ktlint:standard:package-name")

package androidx.core.content.pm

import android.content.pm.PackageInfo
import android.os.Build

/** The one androidx.core class androidx.webkit calls, so extensions don't bundle androidx.core. */
object PackageInfoCompat {
    @JvmStatic
    @Suppress("DEPRECATION")
    fun getLongVersionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}
