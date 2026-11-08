package com.socialsaver

import java.net.HttpURLConnection
import java.net.URL

/** Reads the public page behind an Instagram / Facebook link and finds the media files. */
object Extractor {
    class Option(val label: String, val url: String)
    class Result(val video: Boolean, val options: List<Option>)

    const val UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    fun open(url: String, ua: String = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }

    fun fetch(link: String): Result {
        val conn = open(link, UA)
        val sb = ByteArray(3_000_000)
        var len = 0
        conn.inputStream.use { inp ->
            while (len < sb.size) {
                val n = inp.read(sb, len, sb.size - len)
                if (n < 0) break
                len += n
            }
        }
        val finalUrl = conn.url.toString()
        val html = String(sb, 0, len, Charsets.UTF_8)
        if (finalUrl.contains("/accounts/login") || finalUrl.contains("/login")) {
            throw Exception("This post needs a login (private account or blocked).")
        }

        val videos = LinkedHashMap<String, String>()
        fun grab(label: String, vararg keys: String) {
            for (k in keys) {
                val m = Regex("\"?$k\"?\\s*[:=]\\s*\"([^\"]+)\"").find(html)
                if (m != null) { videos[label] = clean(m.groupValues[1]); return }
            }
        }
        grab("HD", "browser_native_hd_url", "playable_url_quality_hd", "hd_src")
        grab("SD", "browser_native_sd_url", "playable_url", "sd_src")
        if (videos.isEmpty()) {
            val og = meta(html, "og:video:secure_url") ?: meta(html, "og:video:url") ?: meta(html, "og:video")
            if (og != null) videos["Original"] = clean(og)
        }
        if (videos["HD"] != null && videos["HD"] == videos["SD"]) videos.remove("SD")
        if (videos.isNotEmpty()) {
            return Result(true, videos.map { Option(it.key + " quality", it.value) })
        }

        val img = meta(html, "og:image")?.let { clean(it) }
        if (img != null && !img.contains("rsrc.php")) {
            return Result(false, listOf(Option("Original image", img)))
        }
        throw Exception("No downloadable file found. The post may be private, or Instagram/Facebook blocked the request.")
    }

    private fun meta(html: String, prop: String): String? {
        for (m in Regex("<meta\\s[^>]*>").findAll(html)) {
            val tag = m.value
            val p = Regex("(?:property|name)=[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)
            if (p == prop) return Regex("content=[\"']([^\"']*)[\"']").find(tag)?.groupValues?.get(1)
        }
        return null
    }

    private fun clean(s: String): String {
        var r = s.replace("\\/", "/")
        r = Regex("\\\\u([0-9a-fA-F]{4})").replace(r) { it.groupValues[1].toInt(16).toChar().toString() }
        return r.replace("&amp;", "&").replace("&quot;", "\"")
    }
}
