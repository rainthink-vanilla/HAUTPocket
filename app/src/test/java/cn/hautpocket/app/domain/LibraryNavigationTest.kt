package cn.hautpocket.app.domain

import org.junit.Assert.*
import org.junit.Test

class LibraryNavigationTest {
    @Test fun catalogDesignIsRestrictedToVerifiedCatalogPages() {
        assertTrue(OfficialServices.isLibraryCatalog(OfficialServices.LIBRARY_CATALOG))
        // Actual default redirect target; omitting this prevented the first injection entirely.
        assertTrue(OfficialServices.isLibraryCatalog("https://libspace.haut.edu.cn/mspace/index"))
        assertTrue(OfficialServices.isLibraryCatalog("https://libspace.haut.edu.cn/mspace/search"))
        assertTrue(OfficialServices.isLibraryCatalog("https://libspace.haut.edu.cn/mspace/searchList"))
        assertTrue(OfficialServices.isLibraryCatalog("https://libspace.haut.edu.cn/mspace/searchDetailLocal/book123"))
        for (url in listOf("https://libspace.haut.edu.cn/mspace/login", "https://libspace.haut.edu.cn/mspace/user",
            "http://libspace.haut.edu.cn/mspace/searchList", "https://libspace.haut.edu.cn.evil.test/mspace/searchList",
            "https://user@libspace.haut.edu.cn/mspace/searchList", "https://libspace.haut.edu.cn:8443/mspace/searchList")) {
            assertFalse(url, OfficialServices.isLibraryCatalog(url))
        }
    }
    @Test fun recognizesLibraryDocumentAndSchoolLoginWithoutDependingOnCredentials() {
        assertTrue(OfficialServices.isLibraryH5(OfficialServices.LIBRARY_SEATS))
        assertTrue(OfficialServices.isLibraryH5("https://wslib.haut.edu.cn/h5/index.html#/"))
        assertTrue(OfficialServices.isLibraryLogin("https://mapp.haut.edu.cn/authentication/login/casLogin"))
        assertTrue(OfficialServices.isLibraryLogin("https://mapp.haut.edu.cn/cas/login"))
        assertFalse(OfficialServices.isLibraryLogin(OfficialServices.LIBRARY_SEATS))
    }
    @Test fun rejectsLookalikeHostsInsecureOriginsAndUnrelatedPages() {
        for (url in listOf("http://wslib.haut.edu.cn/h5/index.html", "https://wslib.haut.edu.cn.evil.test/h5/index.html",
            "https://wslib.haut.edu.cn:8443/h5/index.html", "https://user@wslib.haut.edu.cn/h5/index.html",
            "https://wslib.haut.edu.cn/h5/index.html/other", OfficialServices.TEACHING)) {
            assertFalse(url, OfficialServices.isLibraryH5(url))
        }
        assertFalse(OfficialServices.isLibraryLogin("https://mapp.haut.edu.cn/"))
        assertFalse(OfficialServices.isLibraryLogin("https://mapp.haut.edu.cn.evil.test/cas/login"))
    }
}
