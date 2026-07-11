import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XYZ Comics"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "XYZ Comics"
        lang = "en"
        baseUrl = "https://xyzcomics.com"
    }
}
