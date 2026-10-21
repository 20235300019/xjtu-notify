package com.xjtu.notify

import com.xjtu.notify.net.Http
import com.xjtu.notify.parse.IntlSource
import com.xjtu.notify.parse.JwcSource
import com.xjtu.notify.parse.MecSource
import com.xjtu.notify.parse.PecSource
import com.xjtu.notify.parse.Sources
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@Ignore("手动联网冒烟测试：gradle testDebugUnitTest --tests '*LiveSmokeTest' -PignoreTests=false")
class LiveSmokeTest {
    @Test fun allSitesLive() {
        val http = Http()
        for ((site, src) in Sources.all) {
            val html = http.getBody(site.home)
            val heads = src.parseList(html, site.home)
            println("LIVE ${site.id}: list=${heads.size} first=${heads.firstOrNull()?.date} ${heads.firstOrNull()?.title?.take(20)}")
            assertTrue("${site.id} parse empty", heads.isNotEmpty())
            val next = src.nextUrl(html, site.home)
            println("LIVE ${site.id} next=$next")
        }
        val d = http.getBody("https://jwc.xjtu.edu.cn/info/1092/10439.htm")
        val body = JwcSource.extractBody(d)
        println("LIVE jwc detail body chars=${body.length}")
        assertTrue("detail body too short", body.length > 500)
    }
}
