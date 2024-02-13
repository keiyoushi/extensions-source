plugins {
    id("lib-multisrc")
}

baseVersionCode = 2

dependencies {
    // Only PeachScan sources uses the image-decoder dependency.
    //noinspection UseTomlInstead
    compileOnly("com.github.tachiyomiorg:image-decoder:fbd6601290")
}
