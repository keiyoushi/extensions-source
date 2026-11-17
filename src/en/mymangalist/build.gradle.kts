import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyMangaList"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www5.mymangalist.org"
    }

    deeplink {
        host("mymangalist.org")
        host("www.mymangalist.org")
        host("www1.mymangalist.org")
        host("www2.mymangalist.org")
        host("www3.mymangalist.org")
        host("www4.mymangalist.org")
        host("www5.mymangalist.org")
        path("/t-..*")
        path("/s-..*")
    }
}
