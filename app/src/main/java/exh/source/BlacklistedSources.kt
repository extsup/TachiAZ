package exh.source

import exh.MERGED_SOURCE_ID

object BlacklistedSources {
    val BLACKLISTED_EXT_SOURCES: List<Long> = emptyList()

    val BLACKLISTED_EXTENSIONS: List<String> = emptyList()

    var HIDDEN_SOURCES =
        listOf(
            MERGED_SOURCE_ID
        )
}
