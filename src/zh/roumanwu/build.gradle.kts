plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    versionCode = 20
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "肉漫屋"
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz3.xyz/dizhi
        baseUrl {
            mirrors(
                "https://rouman5.com",
                "https://roum27.xyz",
            )
        }
    }
}
