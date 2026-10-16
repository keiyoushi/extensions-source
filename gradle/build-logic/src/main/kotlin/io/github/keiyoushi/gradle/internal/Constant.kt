package io.github.keiyoushi.gradle.internal

import org.gradle.accessors.dm.LibrariesForLibs

// To support a new libVersion, add it here, map it in extensionsLib() and add a tachiyomi-lib-vXY catalog entry
internal val VALID_LIB_VERSIONS = listOf("1.6")

internal fun LibrariesForLibs.extensionsLib(libVersion: String) = when (libVersion) {
    "1.6" -> tachiyomi.lib.v16
    else -> error("libVersion $libVersion is not supported. Supported versions: $VALID_LIB_VERSIONS")
}
