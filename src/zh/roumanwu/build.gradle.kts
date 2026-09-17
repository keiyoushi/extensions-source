import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    versionCode = 21
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "肉漫屋"
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz4.xyz/dizhi
        baseUrl {
            mirrors(
                "https://rouman5.com",
                "https://roum29.xyz",
            )
        }
    }

    deeplink {
        host("rouman5.com")
        host("roum29.xyz")
        path("/books/..*")
    }
}
