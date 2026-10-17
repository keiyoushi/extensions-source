import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ArabManhwa"
    versionCode = 57
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "ArabManhwa"
        lang = "ar"
        baseUrl = "https://arabmanhwa.com"
    }
}
