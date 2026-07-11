import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "xkcd"
    versionCode = 17
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://xkcd.com"
    }
    source {
        lang = "es"
        baseUrl = "https://es.xkcd.com"
    }
    source {
        lang = "zh"
        baseUrl = "https://xkcd.tw"
    }
    source {
        lang = "fr"
        baseUrl = "https://xkcd.lapin.org"
    }
    source {
        lang = "ru"
        baseUrl = "https://xkcd.ru"
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}
