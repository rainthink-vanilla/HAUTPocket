package cn.hautpocket.app.domain

import org.junit.Assert.*
import org.junit.Test

class OfficialNavigationTest {
    @Test fun timetableLayoutAppliesOnlyToTheObservedPage() {
        val path = OfficialServices.TEACHING_SCHEDULE_PATH
        assertTrue(OfficialServices.isTeachingSchedule("https://jwglxt.haut.edu.cn$path?gnmkdm=N2151"))
        for (url in listOf("http://jwglxt.haut.edu.cn$path", "https://jwglxt.haut.edu.cn.evil.test$path",
            "https://user@jwglxt.haut.edu.cn$path", "https://jwglxt.haut.edu.cn:8443$path",
            "https://jwglxt.haut.edu.cn/jwglxt/xtgl/login_slogin.html", "https://jwglxt.haut.edu.cn$path/other")) {
            assertFalse(url, OfficialServices.isTeachingSchedule(url))
        }
    }

    @Test fun blankFramesDoNotBecomeExternalAuthenticationErrors() {
        assertTrue(OfficialServices.canNavigate("about:blank", false))
        assertTrue(OfficialServices.canNavigate("about:srcdoc", false))
        assertFalse(OfficialServices.canNavigate("about:blank", true))
        for (main in listOf(true, false)) {
            assertTrue(OfficialServices.canNavigate(OfficialServices.STUDENT_PORTAL, main))
            assertFalse(OfficialServices.canNavigate("https://haut.edu.cn.evil.test/", main))
            assertFalse(OfficialServices.canNavigate("file:///data/private", main))
            assertFalse(OfficialServices.canNavigate("intent://anything", main))
        }
    }

    @Test fun legacyTeachingGetLinksUpgradeWithoutLosingEncodedParameters() {
        assertEquals(OfficialServices.TEACHING, OfficialServices.secureTeachingLink("http://jwglxt.haut.edu.cn/", "GET"))
        assertEquals("https://jwglxt.haut.edu.cn/jwglxt/xtgl/login_slogin.html?service=a%2Fb%3Fc%3Dd#section",
            OfficialServices.secureTeachingLink("http://jwglxt.haut.edu.cn:80/jwglxt/xtgl/login_slogin.html?service=a%2Fb%3Fc%3Dd#section", "GET"))
        assertNull(OfficialServices.secureTeachingLink("http://jwglxt.haut.edu.cn/jwglxt/", "POST"))
        for (url in listOf("http://jwglxt.haut.edu.cn.evil.test/jwglxt/", "http://user@jwglxt.haut.edu.cn/jwglxt/",
            "http://jwglxt.haut.edu.cn:8080/jwglxt/", "http://evil.test/", "http://jwglxt.haut.edu.cn/unrelated",
            "https://jwglxt.haut.edu.cn/", "javascript:alert(1)")) {
            assertNull(url, OfficialServices.secureTeachingLink(url, "GET"))
        }
    }

    @Test fun diagnosticTextContainsOnlyOrigin() {
        assertEquals("https://mapp.haut.edu.cn:8443", OfficialServices.navigationOrigin("https://user:secret@mapp.haut.edu.cn:8443/student/private?ticket=sensitive#token"))
        assertEquals("非网页链接", OfficialServices.navigationOrigin("data:text/plain,private"))
        assertEquals("未知来源", OfficialServices.navigationOrigin("not a url"))
    }
}
