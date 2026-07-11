plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SchaleNetwork"
    versionCode = 20
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf("all", "en", "ja", "zh").forEach {
        source {
            lang = it
            if (it == "en") id = 1484902275639232927L
            baseUrl {
                mirrors(
                    "https://schale.network",
                    "https://anchira.to",
                    "https://gehenna.jp",
                    "https://niyaniya.moe",
                    "https://shupogaki.moe",
                )
            }
        }
    }

    deeplink {
        path("/g/..*/..*")
    }
}
