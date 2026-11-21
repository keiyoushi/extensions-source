import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Longjinman"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "龙禁漫"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://long52.com",
                "https://long92.org",
                "https://long91.xyz",
                "https://long91.org",
                "https://long91.com",
                "https://long7.org",
                "https://long5.org",
                "https://longjm.org",
                "https://52longjm.com",
                "https://long9.xyz",
                "https://long6.xyz",
                "https://long82.com",
                "https://long81.com",
                "https://long7.xyz",
                "https://longjm.com",
            )
        }
    }

    deeplink {
        path("/manhua/..*")
    }
}
