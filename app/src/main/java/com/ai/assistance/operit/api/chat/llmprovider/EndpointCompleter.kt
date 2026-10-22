package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.api.ServiceEndpoint
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Retains upstream's base URL / v1 / complete endpoint convention with strict validation. */
object EndpointCompleter {
    fun modelsEndpoint(value:String):String {
        val chat=completeEndpoint(value).toHttpUrl()
        // Derive the catalog from the same configured API prefix; root-only /models breaks proxy paths.
        return chat.newBuilder().encodedPath(chat.encodedPath.removeSuffix("/chat/completions")+"/models").build().toString()
    }

    fun completeEndpoint(value: String): String {
        val url = ServiceEndpoint.parse(value)
        val path = url.encodedPath.trimEnd('/')
        val completed = when {
            path.isEmpty() -> "/v1/chat/completions"
            path.endsWith("/v1") -> "$path/chat/completions"
            path.endsWith("/chat/completions") -> path
            else -> throw IllegalArgumentException("地址须为域名、/v1 基地址或完整 /chat/completions 接口。")
        }
        return url.newBuilder().encodedPath(completed).build().toString()
    }
}
