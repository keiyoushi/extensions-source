import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikmmh"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "爱看漫"
        lang = "zh"
        baseUrl = "https://ymcdnyfqdapp.ikmmh.com"
    }

    deeplink {
        path("/book/..*")
    }
}
