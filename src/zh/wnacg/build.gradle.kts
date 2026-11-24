import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WNACG"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "紳士漫畫"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://www.cloudwn.shop",
                "https://www.wnmanga.shop",
                "https://www.wnacg.buzz",
                "https://www.wnacg.site",
                "https://www.wn01.cfd",
                "https://www.wnacg.com",
            )
        }
    }
}
