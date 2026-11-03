package com.magral.muzykoisk.sources

/** Public HTML catalog discovery, no API key or server.
 * These adapters discover links from the front page, not full-site results.
 */
object SourceRegistry {
    fun keyless(): List<MusicSource> = listOf(
        HtmlCatalogSource("muzbab", "https://muzbab.org/", Regex("/[^/]+\\.html$|/song/|/track/")),
        HtmlCatalogSource("muzyqa", "https://muzyqa.com/", Regex("/\\d+[^/]*\\.html$")),
        HtmlCatalogSource("nishevo", "https://nishevo.com/", Regex("/track/"))
    )
}
