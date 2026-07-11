import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Naver Comic"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Naver Webtoon"
        lang = "ko"
        baseUrl = "https://comic.naver.com"
    }

    source {
        name = "Naver Webtoon Best Challenge"
        lang = "ko"
        baseUrl = "https://comic.naver.com"
    }

    source {
        name = "Naver Webtoon Challenge"
        lang = "ko"
        baseUrl = "https://comic.naver.com"
    }
}
