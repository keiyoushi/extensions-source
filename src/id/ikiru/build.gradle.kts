import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikiru"
    pkgName = "id.mangatale"
    versionCode = 49
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl = "https://07.ikiru.wtf"
        // Formerly "MangaTale"
        id = 1532456597012176985L
    }
}
