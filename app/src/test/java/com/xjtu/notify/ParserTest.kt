package com.xjtu.notify

import com.xjtu.notify.net.javaStringHash
import com.xjtu.notify.parse.IntlSource
import com.xjtu.notify.parse.JwcSource
import com.xjtu.notify.parse.MecSource
import com.xjtu.notify.parse.PecSource
import com.xjtu.notify.parse.WenSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String): String =
    object {}.javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

class ParserTest {
    @Test fun jwcList() {
        val heads = JwcSource.parseList(fixture("jwc_list.html"), "https://jwc.xjtu.edu.cn/jxxx/jxtz2.htm")
        assertTrue("got ${heads.size}", heads.size >= 5)
        assertTrue(heads.first().url.startsWith("https://jwc.xjtu.edu.cn/info/"))
        assertTrue(heads.all { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it.date) })
        val next = JwcSource.nextUrl(fixture("jwc_list.html"), "https://jwc.xjtu.edu.cn/jxxx/jxtz2.htm")
        assertEquals("https://jwc.xjtu.edu.cn/jxxx/jxtz2/618.htm", next)
    }

    @Test fun mecList() {
        val heads = MecSource.parseList(fixture("mec_list.html"), "https://mec.xjtu.edu.cn/index/tzgg/bks.htm")
        assertTrue("got ${heads.size}", heads.size >= 5)
        assertEquals("2026-09-04", heads.first().date)
        assertTrue(heads.first().title.contains("推免") || heads.first().title.contains("免试"))
        assertEquals("https://mec.xjtu.edu.cn/index/tzgg/bks/26.htm",
            MecSource.nextUrl(fixture("mec_list.html"), "https://mec.xjtu.edu.cn/index/tzgg/bks.htm"))
    }

    @Test fun intlList() {
        val heads = IntlSource.parseList(fixture("intl_list.html"), "https://international.xjtu.edu.cn/hwxx1.htm")
        assertTrue("got ${heads.size}", heads.size >= 5)
        assertTrue(heads.all { it.url.startsWith("https://international.xjtu.edu.cn/info/") })
    }

    @Test fun pecList() {
        val heads = PecSource.parseList(fixture("pec_list.html"), "https://pec.xjtu.edu.cn/xxgg/tzgg.htm")
        assertTrue("got ${heads.size}", heads.size >= 5)
        assertEquals("2026-07-31", heads.first().date)
    }

    @Test fun wenList() {
        val heads = WenSource.parseList(fixture("wen_list.html"), "https://wen.xjtu.edu.cn/tzgg.htm")
        assertTrue("got ${'$'}{heads.size}", heads.size >= 5)
        assertEquals("2026-05-14", heads.first().date)
        assertTrue(heads.first().title.isNotBlank())
        assertTrue(heads.all { it.url.startsWith("https://wen.xjtu.edu.cn/info/") })
    }

    @Test fun detailBodies() {
        assertTrue(JwcSource.extractBody(fixture("jwc_detail.html")).length > 1000)
        assertTrue(MecSource.extractBody(fixture("mec_detail.html")).length > 800)
        assertTrue(PecSource.extractBody(fixture("pec_detail.html")).length > 200)
        assertTrue(WenSource.extractBody(fixture("wen_detail.html")).length > 200)
    }
}

class WafTest {
    @Test fun javaHashKnown() {
        assertEquals(96354, javaStringHash("abc"))
        assertEquals(0, javaStringHash(""))
    }

    @Test fun challengePageParse() {
        val html = fixture("challenge.html")
        val cid = Regex("challengeId = '([^']+)'").find(html)!!.groupValues[1]
        val a = Regex("var a = (\\d+)").find(html)!!.groupValues[1].toInt()
        val b = Regex("var b = (\\d+)").find(html)!!.groupValues[1].toInt()
        val op = Regex("var operator = '([^']+)'").find(html)!!.groupValues[1]
        assertTrue(cid.isNotBlank()); assertTrue(a > 0 && b > 0)
        assertTrue(op in listOf("+", "-", "*"))
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        val h = Math.abs(javaStringHash(cid + (a * b) + ua.substring(0, 10)))
        assertTrue(h >= 0)
    }
}
