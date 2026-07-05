plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    versionCode = 20
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "肉漫屋"
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz3.xyz/dizhi
        baseUrl("https://rouman5.com") {
            mirrors = listOf(
                "https://roum27.xyz",
            )
        }
    }
}
