import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Idol. gravureprincess .date"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://idol.gravureprincess.date"
    }
}
