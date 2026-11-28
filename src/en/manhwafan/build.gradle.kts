import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaFan"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zinmangaart"
    pkgName = "en.manhwaclan"

    source {
        id = 3313733609433811176L
        baseUrl = "https://www.manhwafan.com"
        lang = "en"
    }
}
