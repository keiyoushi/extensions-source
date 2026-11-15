import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manwa Manhua"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "漫蛙漫画"
        lang = "zh"
        baseUrl {
            custom("https://manwadz.cc")
        }
    }

    deeplink {
        host("manwadz.cc")
        host("manwads.cc")
        host("manwabb.cc")
        host("manwacn.cc")
        host("manwarr.cc")
        host("manwaxu.cc")
        host("manwayy.cc")
        host("manwazz.cc")
        host("mwmwbu.cc")
        host("mwmwff.cc")
        path("/comic/..*")
    }
}
