import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fastest Manga"
    theme = "madara"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.fastestmanga.com/en"
    }

    source {
        lang = "tr"
        baseUrl = "https://www.fastestmanga.com/tr"
    }

    source {
        lang = "ar"
        baseUrl = "https://www.fastestmanga.com/ar"
    }

    deeplink {
        host("fastestmanga.com")
        path("/.*/..*")
    }
}
