import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Timeline Comics"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://timelinecomics.blogspot.com"
    }

    deeplink {
        path("/..*/..*/..*.html")
        path("/search/label/..*")
    }
}
