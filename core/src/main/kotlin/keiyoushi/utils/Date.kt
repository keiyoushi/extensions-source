package keiyoushi.utils

import java.text.ParsePosition
import java.text.SimpleDateFormat

fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return parse(date, ParsePosition(0))?.time ?: 0L
}
