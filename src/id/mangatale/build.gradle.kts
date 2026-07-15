import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikiru"
    versionCode = 48
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl = "https://06.ikiru.wtf"
        // Formerly "MangaTale"
        id = 1532456597012176985L
    }
}
