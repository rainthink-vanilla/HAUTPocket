package cn.hautpocket.app.domain

import org.junit.Assert.*
import org.junit.Test

class CatalogSnapshotTest {
    @Test fun onlyPublicOfficialCoverSourcesAreAcceptedWithoutCredentials() {
        val valid = "https://img3m7.ddimg.cn/55/28/book.jpg"
        assertEquals(valid, CatalogSnapshot.cover(valid))
        assertEquals("https://img.hwlsp.cn/cover.jpg", CatalogSnapshot.cover("https://img.hwlsp.cn/cover.jpg"))
        for (url in listOf("http://img3m7.ddimg.cn/a.jpg", "file:///a.jpg", "https://ddimg.cn.evil.example/a.jpg",
            "https://user:secret@libspace.haut.edu.cn/a.jpg", "https://libspace.haut.edu.cn/a.jpg?token=x", "https://unknown.example/a.jpg"))
            assertEquals("", CatalogSnapshot.cover(url))
        val book = CatalogSnapshot.parse("""{"kind":"results","books":[{"index":0,"title":"书","coverUrl":"$valid"}]}""").books.single()
        assertEquals(valid, book.coverUrl)
    }
    @Test fun resultPreservesOfficialRowIdentityAndBibliography() {
        val result = CatalogSnapshot.parse("""{"kind":"results","books":[{"index":3,"title":"书名含\"引号\"","bibliography":"作者 / 出版社","resources":"纸本资源"}],"finished":false,"loading":true}""")
        assertEquals(3, result.books.single().index)
        assertEquals("书名含\"引号\"", result.books.single().title)
        assertEquals("作者 / 出版社", result.books.single().bibliography)
        assertTrue(result.loading)
        assertFalse(result.finished)
    }

    @Test fun holdingsKeepLocationCallNumberAndUnknownStatusWithoutInventingAvailability() {
        val result = CatalogSnapshot.parse("""{"kind":"detail","title":"示例书目","holdings":[{"location":"总馆八楼","lines":["索书号：TS853/65-4","条码号：123"],"status":"可借"},{"location":"分馆","lines":[]}] }""")
        assertEquals("总馆八楼", result.holdings[0].location)
        assertEquals("索书号：TS853/65-4", result.holdings[0].lines[0])
        assertEquals("可借", result.holdings[0].status)
        assertEquals("", result.holdings[1].status)
    }

    @Test fun emptyResultsAndLoginPagesAreDistinct() {
        val empty = CatalogSnapshot.parse("""{"kind":"results","empty":true,"finished":true}""")
        assertTrue(empty.empty)
        assertTrue(empty.books.isEmpty())
        val login = CatalogSnapshot.parse("""{"kind":"official"}""")
        assertFalse(login.empty)
        assertEquals("official", login.kind)
    }

    @Test fun malformedRowsAndUnknownPagesFailRatherThanProducingClickablePlaceholders() {
        for (value in listOf(
            "{}", """{"kind":"unexpected"}""",
            """{"kind":"results","books":[{"index":-1,"title":"书"}]}""",
            """{"kind":"results","books":[{"index":0,"title":" "}]}""",
            "x".repeat(1_000_001)
        )) assertTrue(runCatching { CatalogSnapshot.parse(value) }.isFailure)
    }
}
