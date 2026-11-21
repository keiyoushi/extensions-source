import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Milftoon"
    versionCode = 60
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://milftoon.xxx"
    }

    deeplink {
        path("/comics/..*")
    }
}
