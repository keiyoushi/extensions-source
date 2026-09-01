package androidx.core.content.pm

import android.content.pm.PackageInfo
import android.os.Build

// Shim satisfying androidx.webkit's only androidx.core reference
object PackageInfoCompat {
    @JvmStatic
    fun getLongVersionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) {
        info.getLongVersionCode()
    } else {
        info.versionCode.toLong() and 0xFFFFFFFFL
    }
}
