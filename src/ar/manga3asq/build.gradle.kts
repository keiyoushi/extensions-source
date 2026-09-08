import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "3asq"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        name = "مانجا العاشق"
        lang = "ar"
        baseUrl {
            custom("https://3asq.online")
        }
    }
}
