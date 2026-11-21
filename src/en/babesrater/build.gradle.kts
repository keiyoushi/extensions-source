import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Babes Rater"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://babesrater.com"
    }

    deeplink {
        path("/person/..*")
        path("/gallery/..*")
        path("/picture/..*")
        path("/infinite-scroll/..*")
    }
}
