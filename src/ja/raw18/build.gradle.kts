import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raw18"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "ja"
        baseUrl {
            custom("https://raw18.vip")
        }
    }
}
