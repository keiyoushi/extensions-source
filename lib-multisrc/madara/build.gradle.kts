plugins {
    id("lib-multisrc")
}

baseVersionCode = 38

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}
