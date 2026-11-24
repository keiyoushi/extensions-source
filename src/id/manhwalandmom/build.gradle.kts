import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaLand.mom"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "manhwaindo"

    source {
        lang = "id"
        baseUrl {
            custom("https://link.manhwaland.land")
        }
    }
}
