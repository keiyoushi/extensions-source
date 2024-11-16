package eu.kanade.tachiyomi.extension.vi.truyentranhdammy

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

@SuppressLint("SimpleDateFormat")
class TruyenTranhDamMy : Madara(
    "Truyện tranh đam mỹ",
    "https://truyennhameo.com",
    "vi",
    dateFormat = SimpleDateFormat(
        "MMMM d, yyyy",
        DateFormatSymbols(Locale("vi")).apply {
            // Month One, Month Two, ...
            months = arrayOf(
                "Tháng Một",
                "Tháng Hai",
                "Tháng Ba",
                "Tháng Tư",
                "Tháng Năm",
                "Tháng Sáu",
                "Tháng Bảy",
                "Tháng Tám",
                "Tháng Chín",
                "Tháng Mười",
                "Tháng Mười Một",
                "Tháng Mười Hai",
            )
        },
    ),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
