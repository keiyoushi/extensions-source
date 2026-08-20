import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read One-Punch Man Manga Online"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww7.readopm.com"
    }
}
