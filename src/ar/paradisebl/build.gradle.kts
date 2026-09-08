import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Paradise BL"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://paradise-bl.com"
    }
}
