package top.colter.dynamic.draw.image

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpImageDownloaderTest {
    @Test
    fun `upgrade hdslb http image urls to https`() {
        val original = URI("http://i1.hdslb.com/bfs/archive/demo.jpg?foo=bar#fragment")

        val normalized = normalizeRemoteImageUri(original)

        assertEquals("https://i1.hdslb.com/bfs/archive/demo.jpg?foo=bar#fragment", normalized.toString())
    }

    @Test
    fun `keep non hdslb http urls unchanged`() {
        val original = URI("http://example.com/image.jpg")
        val lookalike = URI("http://not-hdslb.com/image.jpg")

        assertEquals(original, normalizeRemoteImageUri(original))
        assertEquals(lookalike, normalizeRemoteImageUri(lookalike))
    }

    @Test
    fun `keep existing https hdslb urls unchanged`() {
        val original = URI("https://i0.hdslb.com/bfs/archive/demo.jpg")

        assertEquals(original, normalizeRemoteImageUri(original))
    }
}
