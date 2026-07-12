import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComicsKingdom"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "es").forEach {
        source {
            name = "Comics Kingdom"
            lang = it
            baseUrl = "https://wp.comicskingdom.com"
        }
    }
}
