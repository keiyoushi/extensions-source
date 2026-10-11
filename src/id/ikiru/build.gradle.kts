import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikiru"
    pkgName = "id.mangatale"
    versionCode = 60
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://09.ikiru.wtf"
        // Formerly "MangaTale"
        id = 1532456597012176985L
    }
}
