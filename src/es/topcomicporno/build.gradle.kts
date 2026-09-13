import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TopComicPorno"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://topcomicporno.com"
    }
}
