import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Catoons"
    versionCode = 53
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://newcat1.xyz"
    }
}
