import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Guazimanhua"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "瓜子漫画"
        lang = "zh"
        baseUrl = "https://www.guazimanhua.com"
    }

    deeplink {
        host("guazimanhua.com")
        host("www.guazimanhua.com")
        path("/comic.php.*")
        path("/chapter.php.*")
    }
}
