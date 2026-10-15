import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MomonGA"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "momon:GA"
        lang = "ja"
        baseUrl = "https://momon-ga.com"
    }
}
