import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Origin"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl = "https://teammangaorigin.blogspot.com"
    }

    deeplink {
        host("teammangaorigin.blogspot.com")
        host("mangaoriginread.blogspot.com")
        path("/..*/..*/..*.html")
        path("/search/label/..*")
    }
}
