import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Averis Fansub"
    theme = "madara"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://averisfansub.site"
        lang = "tr"
    }

    deeplink {
        host("www.averisfansub.site")
        path("/.*/..*")
    }
}
