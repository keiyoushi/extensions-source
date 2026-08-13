import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kaguya"
    versionCode = 4
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://02.kaguya.pro"
        id = 1557304490417397104L
    }
}
