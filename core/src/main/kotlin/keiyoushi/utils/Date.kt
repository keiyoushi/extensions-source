package keiyoushi.utils

import java.text.ParseException
import java.text.SimpleDateFormat

fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return try {
        parse(date)!!.time
    } catch (_: ParseException) {
        0L
    }
}
