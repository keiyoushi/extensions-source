plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BoyLove"
    versionCode = 18
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "香香腐宅"
        lang = "zh"

        // redirect URL: https://fuhouse.info/bl
        // link source URL: https://boylovepage.github.io
        baseUrl {
            mirrors(
                "https://boylove.cc",
                "https://boylove4.xyz",
            )
        }
    }
}
