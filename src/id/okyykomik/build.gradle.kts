import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OkyyKomik"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.okyykomik.my.id"
    }
}
