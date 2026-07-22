import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakuri"
    versionCode = 34
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://lc2.mangakuri.online"
        versionId = 2
    }
}
