import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReYume"
    versionCode = 29
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.re-yume.my.id"
    }
}
