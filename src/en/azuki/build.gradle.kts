import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Omoi"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.omoi.com"
        versionId = 2
    }
}
