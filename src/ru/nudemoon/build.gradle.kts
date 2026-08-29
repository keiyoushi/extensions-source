import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nude-Moon"
    versionCode = 30
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        baseUrl {
            custom("https://nude-moon.org")
        }
        lang = "ru"
    }
}
