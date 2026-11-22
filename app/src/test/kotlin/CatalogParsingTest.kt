package app.lumira.wallpapers

import app.lumira.wallpapers.data.shop.Catalog
import app.lumira.wallpapers.data.shop.CatalogItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the published catalog.json contract used by the shop. */
class CatalogParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parses a minimal catalog`() {
        val raw = """
            {
              "version": 1,
              "baseUrl": "https://example.com/catalog",
              "categories": [{"id":"space","name":"Espacio","icon":"star","color":"#8B7BFF"}],
              "items": [{
                "id":"nebula","title":"Nebulosa","category":"space","kind":"image",
                "thumb":"thumbs/nebula.webp","full":"https://example.com/a.jpg",
                "width":1080,"height":1920,"bytes":123,"license":"CC0-1.0",
                "tags":["espacio","oscuro"],"popularity":10,"addedAt":1
              }]
            }
        """.trimIndent()

        val catalog = json.decodeFromString<Catalog>(raw)
        assertEquals(1, catalog.version)
        assertEquals(1, catalog.categories.size)
        assertEquals(1, catalog.items.size)
        val item = catalog.items.first()
        assertEquals("Nebulosa", item.title)
        assertTrue(!item.isVideo)
        assertEquals(
            "https://example.com/catalog/thumbs/nebula.webp",
            item.thumbUrl(catalog.baseUrl),
        )
        assertEquals("https://example.com/a.jpg", item.fullUrl(catalog.baseUrl))
    }

    @Test
    fun `resolves relative and absolute urls`() {
        val base = "https://cdn.example.com/catalog/"
        val item = CatalogItem(
            id = "a", title = "A", category = "c",
            thumb = "thumbs/a.webp",
            preview = "https://other.example.com/p.mp4",
            full = "/files/a.mp4",
        )
        assertEquals("https://cdn.example.com/catalog/thumbs/a.webp", item.thumbUrl(base))
        assertEquals("https://other.example.com/p.mp4", item.previewUrl(base))
        assertEquals("https://cdn.example.com/catalog/files/a.mp4", item.fullUrl(base))
    }
}
