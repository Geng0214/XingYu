package com.ai.assistance.operit.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Keep user-provided outbound service addresses under the same HTTPS and privacy rules. */
object ServiceEndpoint {
    const val MAX_LENGTH=2048

    fun parse(raw:String):HttpUrl {
        require(raw.length<=MAX_LENGTH) {"服务地址不能超过 $MAX_LENGTH 个字符。"}
        val url=raw.trim().toHttpUrl()
        require(url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.query==null&&url.fragment==null) {
            "请填写不含账号、查询参数或片段的 HTTPS 地址。"
        }
        return url
    }
}
