import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raw18"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "ja"
        baseUrl {
            custom("https://raw18.casa")
        }
    }
}
