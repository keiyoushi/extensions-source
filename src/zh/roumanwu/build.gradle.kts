plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    className = "Roumanwu"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz3.xyz/dizhi
        baseUrl("https://rouman5.com") {
            mirrors = listOf(
                "https://roum27.xyz",
            )
        }
    }
}
