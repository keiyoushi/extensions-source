import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Bab"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "مانجا باب"
        lang = "ar"
        baseUrl {
            custom("https://mangabab.com")
        }
        id = 6899943547168982381L
    }
}
