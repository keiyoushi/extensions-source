plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wolf.com"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "늑대닷컴 - 웹툰"
        lang = "ko"
        baseUrl("https://wfwf411.com") {
            withCustom = true
        }
    }

    source {
        name = "늑대닷컴 - 만화책"
        lang = "ko"
        baseUrl("https://wfwf411.com") {
            withCustom = true
        }
    }

    source {
        name = "늑대닷컴 - 포토툰"
        lang = "ko"
        baseUrl("https://wfwf411.com") {
            withCustom = true
        }
    }
}
