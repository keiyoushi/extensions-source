import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raw18"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "ja"
        baseUrl {
            custom("https://raw18.quest")
        }
    }
}
