plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadComicOnline"
    versionCode = 44
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://readcomiconline.li",
                "https://rcostation.xyz",
            )
        }
    }

    deeplink {
        path("/Comic/..*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
