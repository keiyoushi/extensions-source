import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Copy Manga"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "拷貝漫畫"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://www.mangacopy.com",
                "https://www.copy4000.com",
                "https://www.copy5000.com",
                "https://www.copy20.com",
                "https://www.copymanga.fun",
            )
        }
    }

    deeplink {
        host("www.mangacopy.com")
        host("mangacopy.com")
        host("www.copy4000.com")
        host("copy4000.com")
        host("www.copy5000.com")
        host("copy5000.com")
        host("www.copy20.com")
        host("copy20.com")
        host("www.copymanga.fun")
        host("copymanga.fun")
        path("/comic/..*")
        path("/h5/details/comic/..*")
        path("/h5/comicContent/..*")
    }
}
