package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.TextMaterialReader
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.TextMaterials
import java.io.ByteArrayInputStream
import java.net.Proxy
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class TextMaterialReaderTest {
    private fun rejected(code:String,block:()->Unit) {
        try {block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}
    }

    @Test fun localFilesRequireSupportedNameMimeSizeAndStrictUtf8() {
        val reader=TextMaterialReader()
        val markdown=reader.file(ByteArrayInputStream("# 标题\r\n内容🙂  \r\n".toByteArray()),"notes.md","text/plain; charset=utf-8")
        assertEquals(TextMaterials.KIND_FILE,markdown.kind);assertEquals(TextMaterials.MIME_MARKDOWN,markdown.mimeType)
        assertEquals("# 标题\n内容🙂",markdown.text);assertNull(markdown.sourceUrl);markdown.validate()
        rejected("MATERIAL_TYPE"){reader.file(ByteArrayInputStream("text".toByteArray()),"notes.pdf","text/plain")}
        rejected("MATERIAL_TYPE"){reader.file(ByteArrayInputStream("text".toByteArray()),"notes.txt","application/pdf")}
        rejected("MATERIAL_ENCODING"){reader.file(ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28)),"bad.txt","text/plain")}
        rejected("MATERIAL_LIMIT"){reader.file(ByteArrayInputStream(ByteArray(TextMaterials.MAX_FILE_BYTES+1)),"large.txt","text/plain")}
    }

    @Test fun longTextIsExplicitlyTruncatedToTheReviewedExcerpt() {
        val reader=TextMaterialReader()
        val source="甲".repeat(TextMaterials.MAX_CONTEXT_CHARS+37)
        val result=reader.file(ByteArrayInputStream(source.toByteArray()),"long.txt","text/plain")
        assertTrue(result.truncated);assertEquals(source.length,result.originalChars)
        assertEquals(TextMaterials.MAX_CONTEXT_CHARS,result.text.length);assertEquals(TextMaterials.sha256(result.text),result.sha256)
    }

    @Test fun httpsHtmlIsSanitizedAndRequestCarriesNoCredentials()=tls {server,reader->
        server.enqueue(MockResponse().setHeader("Content-Type","text/html; charset=utf-8").setBody("""
            <html><head><title> 安全 &amp; 预览 </title><style>PRIVATE_STYLE</style></head>
            <body><h1>标题</h1><script>PRIVATE_SCRIPT()</script><p>正文 &lt;内容&gt;</p><a href='https://evil.invalid'>链接文字</a></body></html>
        """.trimIndent()))
        val result=reader.link(server.url("/article?x=1#fragment").toString())
        assertEquals("安全 & 预览",result.displayName);assertEquals(TextMaterials.MIME_HTML,result.mimeType)
        assertTrue(result.text.contains("标题"));assertTrue(result.text.contains("正文 <内容>"));assertTrue(result.text.contains("链接文字"))
        assertFalse(result.text.contains("PRIVATE_SCRIPT"));assertFalse(result.text.contains("PRIVATE_STYLE"));assertFalse(result.sourceUrl!!.contains('#'))
        val request=server.takeRequest();assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));assertEquals(1,server.requestCount)
    }

    @Test fun redirectUnsupportedTypeAndNonHttpsAreRejectedWithoutSecondRequest()=tls {server,reader->
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location",server.url("/second")))
        rejected("LINK_REDIRECT"){reader.link(server.url("/first").toString())}
        assertEquals(1,server.requestCount)
        server.enqueue(MockResponse().setHeader("Content-Type","application/octet-stream").setBody("binary"))
        rejected("MATERIAL_TYPE"){reader.link(server.url("/binary").toString())}
        server.enqueue(MockResponse().setHeader("Content-Type","text/plain; charset=iso-8859-1").setBody("legacy"))
        rejected("MATERIAL_ENCODING"){reader.link(server.url("/encoding").toString())}
        server.enqueue(MockResponse().setHeader("Content-Type","text/plain").setBody("x".repeat(TextMaterials.MAX_LINK_BYTES+1)))
        rejected("MATERIAL_LIMIT"){reader.link(server.url("/large").toString())}
        rejected("LINK_URL"){reader.link("http://example.com/")}
        rejected("LINK_URL"){reader.link("https://user:password@example.com/")}
        assertEquals(4,server.requestCount)
    }

    private fun tls(block:(MockWebServer,TextMaterialReader)->Unit) {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use {server->
            server.useHttps(serverTls.sslSocketFactory(),false);server.start(java.net.InetAddress.getByName("localhost"),0)
            val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
            block(server,TextMaterialReader(client))
        }
    }
}
