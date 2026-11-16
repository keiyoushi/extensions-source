import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHot WEB"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "マンガほっと"
        lang = "ja"
        baseUrl = "https://web.mangahot.jp"
    }

    deeplink {
        path("/works/detail.php")
        path("/works/free.php")
        path("/works/view.php")
    }
}
