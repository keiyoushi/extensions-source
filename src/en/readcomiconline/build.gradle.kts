plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadComicOnline"
    className = "Readcomiconline"
    versionCode = 43
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("readcomiconline.li")
        host("rcostation.xyz")
        path("/Comic/..*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
