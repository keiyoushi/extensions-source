package keiyoushi.utils

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val applicationContext: Application get() = Injekt.get()
