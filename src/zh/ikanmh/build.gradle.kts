import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikanmh"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "漫小肆韓漫"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://ikanmh.xyz",
                "https://ikanmh.cc",
                "https://www.jjmhw12.top",
            )
        }
    }

    deeplink {
        host("ikanmh.xyz")
        host("www.ikanmh.xyz")
        host("ikanmh.cc")
        host("www.ikanmh.cc")
        host("jjmhw12.top")
        host("www.jjmhw12.top")
        path("/book/..*")
        path("/chapter/..*")
    }
}
