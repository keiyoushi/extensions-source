plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YellowNote"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "小黄书"
        lang = "zh-Hans"
        baseUrl("https://xchina.co") {
            withCustom = true
        }
        id = 170542391855030753
    }

    source {
        name = "小黄书"
        lang = "zh-Hant"
        baseUrl("https://tw.xchina.co") {
            withCustom = true
        }
    }

    source {
        name = "小黄书"
        lang = "en"
        baseUrl("https://en.xchina.co") {
            withCustom = true
        }
    }

    source {
        name = "小黄书"
        lang = "es"
        baseUrl("https://es.xchina.co") {
            withCustom = true
        }
    }

    source {
        name = "小黄书"
        lang = "ko"
        baseUrl("https://kr.xchina.co") {
            withCustom = true
        }
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
