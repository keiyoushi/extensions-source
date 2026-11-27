import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lezhin Comics"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl = "https://www.lezhin.com"
    }

    deeplink {
        path("/ko/comic/..*")
    }
}
