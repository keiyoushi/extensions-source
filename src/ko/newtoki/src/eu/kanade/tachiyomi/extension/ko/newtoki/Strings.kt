package eu.kanade.tachiyomi.extension.ko.newtoki

import android.os.Build
import android.os.LocaleList
import java.util.Locale

private val useKorean by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        LocaleList.getDefault().getFirstMatch(arrayOf("ko", "en"))?.language == "ko"
    } else {
        Locale.getDefault().language == "ko"
    }
}

// region Prompts

fun solveCaptcha() = when {
    useKorean -> "WebView에서 캡챠 풀기"
    else -> "Solve Captcha with WebView"
}

fun titleNotMatch(realTitle: String) = when {
    useKorean -> "이 만화를 찾으시려면 '$realTitle'으로 검색하세요"
    else -> "Find this manga by searching '$realTitle'"
}

fun needMigration() = when {
    useKorean -> "이 항목은 URL 포맷이 틀립니다. 중복된 항목을 피하려면 동일한 소스로 이전하세요.\n\n"
    else -> "This entry has wrong URL format. Please migrate to the same source to avoid duplicates.\n\n"
}

// endregion

// region Filters

fun ignoredForTextSearch() = when {
    useKorean -> "검색에서 다음 필터 항목은 무시됩니다"
    else -> "The following filters are ignored for text search"
}

// endregion

// region Preferences

fun domainNumberTitle() = when {
    useKorean -> "도메인 번호"
    else -> "Domain number"
}

fun domainNumberSummary() = when {
    useKorean -> "도메인 번호는 자동으로 갱신됩니다"
    else -> "This number is updated automatically"
}

fun editDomainNumber() = when {
    useKorean -> "확장기능 설정에서 도메인 번호를 수정해 주세요"
    else -> "Please edit domain number in extension settings"
}

fun rateLimitTitle() = when {
    useKorean -> "요청 제한"
    else -> "Rate limit"
}

fun rateLimitEntry(period: String) = when {
    useKorean -> "${period}초마다 요청"
    else -> "1 request every $period seconds"
}

// taken from app strings
fun requiresAppRestart() = when {
    useKorean -> "설정을 적용하려면 앱을 재시작하세요"
    else -> "Requires app restart to take effect"
}

// endregion
