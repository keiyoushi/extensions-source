import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18.org"
    versionCode = 2
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manhwa18.org"
    }
}
