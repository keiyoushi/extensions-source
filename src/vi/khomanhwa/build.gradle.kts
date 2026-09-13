import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KhoManhwa"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://khomanhwa.com")
        }
    }

    deeplink {
        path("/.*")
    }
}
