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
        baseUrl {
            custom("https://xchina.co")
        }
        id = 170542391855030753
    }

    source {
        name = "小黄书"
        lang = "zh-Hant"
        baseUrl {
            custom("https://tw.xchina.co")
        }
    }

    source {
        name = "小黄书"
        lang = "en"
        baseUrl {
            custom("https://en.xchina.co")
        }
    }

    source {
        name = "小黄书"
        lang = "es"
        baseUrl {
            custom("https://es.xchina.co")
        }
    }

    source {
        name = "小黄书"
        lang = "ko"
        baseUrl {
            custom("https://kr.xchina.co")
        }
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
