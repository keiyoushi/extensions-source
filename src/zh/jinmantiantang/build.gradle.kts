import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jinman Tiantang"
    versionCode = 62
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "禁漫天堂"
        lang = "zh"
        baseUrl {
            mirrors(
                "主站1" to "https://18comic.vip",
                "主站2" to "https://18comic.ink",
                "东南亚线路1" to "https://jmcomic-zzz.one",
                "东南亚线路2" to "https://jmcomic-zzz.org",
            )
        }
    }

    deeplink {
        path("/album/..*")
        path("/photo/..*")
    }
}
