import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Orca Manga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://infinity896.blogspot.com"
    }
}
