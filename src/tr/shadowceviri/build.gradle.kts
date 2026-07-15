import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shadow Çeviri"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "tr"
        baseUrl = "https://shadowceviri.blogspot.com"
    }
}
