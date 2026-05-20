package keiyoushi.utils

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// This ensures the type token isn't duplicated at each call site
val applicationContext: Application get() = Injekt.get()
