import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wolf.com"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "늑대닷컴 - 웹툰"
        lang = "ko"
        baseUrl {
            custom("https://wfwf426.com")
        }
    }

    source {
        name = "늑대닷컴 - 만화책"
        lang = "ko"
        baseUrl {
            custom("https://wfwf426.com")
        }
    }
}
