import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gaoqingpao"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://www.gaoqingpao.com"
    }

    deeplink {
        path("/g..*\\.html")
        path("/g..*\\.html/")
        path("/tag/..*")
    }
}
