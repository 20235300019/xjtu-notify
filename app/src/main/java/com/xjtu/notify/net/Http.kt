package com.xjtu.notify.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MemCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        for (c in cookies) {
            list.removeAll { it.name == c.name }
            list.add(c)
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
    fun putRaw(host: String, name: String, value: String, https: Boolean = true) {
        val list = store.getOrPut(host) { mutableListOf() }
        list.removeAll { it.name == name }
        val cb = Cookie.Builder().name(name).value(value).domain(host).path("/")
        if (https) cb.secure()
        list.add(cb.build())
    }
}

fun javaStringHash(s: String): Int {
    var h = 0
    for (c in s) h = 31 * h + c.code
    return h
}

class Http(val jar: MemCookieJar = MemCookieJar()) {
    companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun request(url: String) = Request.Builder()
        .url(url).header("User-Agent", UA)
        .header("Accept-Language", "zh-CN,zh;q=0.9")
        .build()

    fun getBody(url: String): String {
        client.newCall(request(url)).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (body.contains("dynamic_challenge") && Regex("challengeId = '").containsMatchIn(body)) {
                solveChallenge(url, body)
                client.newCall(request(url)).execute().use { r2 ->
                    return r2.body?.string() ?: error("空响应")
                }
            }
            if (!resp.isSuccessful && body.isBlank()) error("HTTP ${resp.code}")
            return body
        }
    }

    private fun solveChallenge(pageUrl: String, html: String) {
        val cid = Regex("challengeId = '([^']+)'").find(html)?.groupValues?.get(1) ?: return
        val a = Regex("var a = (\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return
        val b = Regex("var b = (\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return
        val op = Regex("var operator = '([^']+)'").find(html)?.groupValues?.get(1) ?: return
        val ans = when (op) { "+" -> a + b; "-" -> a - b; else -> a * b }
        val hash = abs(javaStringHash(cid + ans.toString() + UA.substring(0, 10)))
        val u = runCatching { pageUrl.toHttpUrl() }.getOrNull() ?: return
        val info = """{"userAgent":"$UA","language":"zh-CN","platform":"Win32",""" +
            """"screen":{"width":1920,"height":1080,"colorDepth":24},""" +
            """"timezoneOffset":-480,"hasTouchEvents":false}"""
        val payload = """{"challenge_id":"$cid","answer":$ans,"browser_info":$info,"hash":$hash}"""
        val req = Request.Builder()
            .url(u.scheme + "://" + u.host + "/dynamic_challenge")
            .header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .header("Referer", pageUrl)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: return
            val obj = runCatching { JSON.parseToJsonElement(txt).jsonObject }.getOrNull() ?: return
            val ok = obj["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true
            val clientId = obj["client_id"]?.jsonPrimitive?.contentOrNullSafe()
            if (ok && clientId != null) jar.putRaw(u.host, "client_id", clientId, u.scheme == "https")
        }
    }

    fun postJson(url: String, json: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${body.take(300)}")
            return body
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.contentOrNullSafe(): String? =
    runCatching { jsonPrimitive.content }.getOrNull()
