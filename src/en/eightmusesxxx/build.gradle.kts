import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "8Muses (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://8muses.xxx"
    }

    deeplink {
        host("8muses.xxx")
        host("www.8muses.xxx")
        path("/comics.*")
        path("/category/..*")
        path("/tag/..*")
    }
}
