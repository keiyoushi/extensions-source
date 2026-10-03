import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Comics Online"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mmrcms"

    source {
        lang = "en"
        baseUrl = "https://readcomicsonline.ru"
    }
}
