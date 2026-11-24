import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ücra Çizgi Roman"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://www.ucracizgiroman.com"
    }

    deeplink {
        host("www.ucracizgiroman.com")
        host("ucracizgiroman.blogspot.com")
        path("/..*/..*/..*.html")
    }
}
