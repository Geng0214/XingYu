package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.api.environment.OpenMeteoService
import com.ai.assistance.operit.data.model.*
import java.net.Proxy
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class EnvironmentTest {
    private val role=RoleEntity("role-a","小岚","温柔交流",spaceId="space-a")
    private val chat=ChatEntity("chat-a",role.id,"config","chat")
    private val candidate=WeatherCandidate(1816670,"杭州","浙江","中国","CN",30.2741,120.1551,"Asia/Shanghai")
    private val snapshot=WeatherSnapshot("2026-09-20T18:00",26.5,27.1,63,0.0,2,8.4,21.0,29.0,20,1_000_000)
    private fun profile(time:Boolean=true,weather:Boolean=true,fetchedAt:Long=snapshot.fetchedAt)=EnvironmentProfile(role.id,role.spaceId,time,weather,
        locationId=candidate.id,locationName=candidate.name,admin1=candidate.admin1,country=candidate.country,countryCode=candidate.countryCode,latitude=candidate.latitude,longitude=candidate.longitude,timezone=candidate.timezone,
        weatherTime=snapshot.weatherTime,temperature=snapshot.temperature,apparentTemperature=snapshot.apparentTemperature,humidity=snapshot.humidity,precipitation=snapshot.precipitation,weatherCode=snapshot.weatherCode,windSpeed=snapshot.windSpeed,todayMin=snapshot.todayMin,todayMax=snapshot.todayMax,todayPrecipitationProbability=snapshot.todayPrecipitationProbability,fetchedAt=fetchedAt,version=3,updatedAt=1_000_001)
    private suspend fun rejected(code:String,block:suspend ()->Unit){try{block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
    private fun tls(block:(MockWebServer,OpenMeteoService)->Unit) {
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use {server->
            server.useHttps(serverTls.sslSocketFactory(),false);server.start(java.net.InetAddress.getByName("localhost"),0)
            val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
            block(server,OpenMeteoService(client,server.url("/v1/search"),server.url("/v1/forecast")))
        }
    }

    @Test fun cityAndForecastUrlsAreFixedHttpsAndEncodeChineseEmoji()=tls {server,service->
        val search=service.searchUrl("杭州🙂, 中国");assertTrue(search.isHttps);assertEquals("杭州🙂, 中国",search.queryParameter("name"));assertEquals("5",search.queryParameter("count"));assertEquals("zh",search.queryParameter("language"))
        val forecast=service.forecastUrl(candidate);assertTrue(forecast.isHttps);assertEquals("30.2741",forecast.queryParameter("latitude"));assertEquals("Asia/Shanghai",forecast.queryParameter("timezone"));assertEquals("3",forecast.queryParameter("forecast_days"));assertEquals(server.hostName,forecast.host)
        try {service.searchUrl("a");fail()}catch(_:IllegalArgumentException){}
    }

    @Test fun decodesBoundedCandidatesAndForecastWhileIgnoringUnknownFields()=tls {server,service->runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"results":[{"id":1816670,"name":"杭州","latitude":30.2741,"longitude":120.1551,"country_code":"CN","country":"中国","admin1":"浙江","timezone":"Asia/Shanghai","unknown":"ignored"}]}"""))
        val found=service.search("杭州");assertEquals(listOf(candidate),found)
        server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"current":{"time":"2026-09-20T18:00","temperature_2m":26.5,"apparent_temperature":27.1,"relative_humidity_2m":63,"precipitation":0.0,"weather_code":2,"wind_speed_10m":8.4},"daily":{"weather_code":[2,3,61],"temperature_2m_max":[29.0,28.0,27.0],"temperature_2m_min":[21.0,20.0,19.0],"precipitation_probability_max":[20,30,60]},"unknown":true}"""))
        assertEquals(snapshot,service.forecast(candidate){1_000_000})
        assertEquals(2,server.requestCount);assertNull(server.takeRequest().getHeader("Authorization"));assertNull(server.takeRequest().getHeader("Authorization"))
    }}

    @Test fun redirectsBadJsonWrongContentAndOversizeFailWithoutFollowing()=tls {server,service->runBlocking {
        for((response,code) in listOf(
            MockResponse().setResponseCode(307).setHeader("Location",server.url("/forwarded")) to "REDIRECT",
            MockResponse().setResponseCode(429) to "RATE_LIMIT",
            MockResponse().setResponseCode(503) to "SERVER",
            MockResponse().setHeader("Content-Type","text/plain").setBody("{}") to "WEATHER_PROTOCOL",
            MockResponse().setHeader("Content-Type","application/json").setBody("{broken") to "WEATHER_PROTOCOL",
            MockResponse().setHeader("Content-Type","application/json").setBody("x".repeat(Environments.RESPONSE_LIMIT+1)) to "WEATHER_LIMIT")) {
            server.enqueue(response);rejected(code){service.search("杭州")}
        }
        assertEquals(6,server.requestCount);repeat(6){server.takeRequest()};assertEquals(6,server.requestCount)
    }}

    @Test fun cancellationStopsInFlightLookupAndDoesNotProduceLateResult()=tls {server,service->runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job=launch {service.search("杭州")};while(server.requestCount==0)delay(5);job.cancelAndJoin();assertTrue(job.isCancelled);assertEquals(1,server.requestCount)
    }}

    @Test fun deterministicTimeAndFreshWeatherAreFrozenWhileStaleDisabledAndRawAreExcluded() {
        val zone=ZoneId.of("Asia/Shanghai");val at=Instant.parse("2026-09-20T10:00:00Z").toEpochMilli()
        val current=profile(fetchedAt=at-Environments.STALE_MS)
        val selected=Environments.select(current,false,at,zone)
        assertEquals("included",selected.timeState);assertEquals("included",selected.weatherState);assertTrue(selected.instruction.contains("2026-09-20 18:00:00 +08:00"));assertTrue(selected.instruction.contains("杭州"));assertFalse(selected.instruction.contains("30.2741"))
        assertEquals("stale",Environments.select(current.copy(fetchedAt=at-Environments.STALE_MS-1),false,at,zone).weatherState)
        assertTrue(Environments.select(current,true,at,zone).instruction.isEmpty())
        val newYork=ZoneId.of("America/New_York")
        val beforeDst=Instant.parse("2026-03-08T06:59:59Z").toEpochMilli()
        val afterDst=Instant.parse("2026-03-08T07:00:00Z").toEpochMilli()
        assertTrue(Environments.select(profile(weather=false),false,beforeDst,newYork).instruction.contains("-05:00"))
        assertTrue(Environments.select(profile(weather=false),false,afterDst,newYork).instruction.contains("-04:00"))
    }

    @Test fun requestContextChecksEnvironmentOwnershipAndDoesNotReadItWithoutExplicitToggles() {
        val now=1_000_000L;val zone=ZoneId.of("UTC")
        val resolved=RequestContext.resolve(role.scope(),role,chat,emptyList(),"你好",emptyList(),environmentProfile=profile(),now=now,deviceZone=zone)
        assertEquals("included",resolved.environment.weatherState);assertTrue(resolved.systemInstruction.contains("用户确认的天气快照"));assertTrue(resolved.systemInstruction.contains("确定的本机时间"))
        val off=RequestContext.resolve(role.scope(),role,chat,emptyList(),"你好",emptyList(),environmentProfile=profile(false,false),now=now,deviceZone=zone)
        assertFalse(off.systemInstruction.contains("天气快照"));assertEquals("disabled",off.environment.timeState)
        try {RequestContext.resolve(role.scope(),role,chat,emptyList(),"你好",emptyList(),environmentProfile=profile().copy(spaceId="foreign"),now=now,deviceZone=zone);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
    }
}
