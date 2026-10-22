package com.ai.assistance.operit.api.environment

import com.ai.assistance.operit.data.model.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class OpenMeteoService(private val baseClient:OkHttpClient=OkHttpClient(),
    private val geocodingEndpoint:HttpUrl=GEOCODING.toHttpUrl(),private val forecastEndpoint:HttpUrl=FORECAST.toHttpUrl()) {
    companion object {
        const val GEOCODING="https://geocoding-api.open-meteo.com/v1/search"
        const val FORECAST="https://api.open-meteo.com/v1/forecast"
    }

    fun searchUrl(city:String):HttpUrl {
        Environments.validateCity(city)
        validateEndpoint(geocodingEndpoint,"/v1/search")
        return geocodingEndpoint.newBuilder().addQueryParameter("name",city).addQueryParameter("count","5")
            .addQueryParameter("language","zh").addQueryParameter("format","json").build()
    }

    fun forecastUrl(candidate:WeatherCandidate):HttpUrl {
        Environments.validateCandidate(candidate)
        validateEndpoint(forecastEndpoint,"/v1/forecast")
        return forecastEndpoint.newBuilder()
            .addQueryParameter("latitude",candidate.latitude.toString()).addQueryParameter("longitude",candidate.longitude.toString())
            .addQueryParameter("current","temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m")
            .addQueryParameter("daily","weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone",candidate.timezone).addQueryParameter("forecast_days","3").build()
    }

    suspend fun search(city:String)=decodeCandidates(fetch(searchUrl(city)))
    suspend fun forecast(candidate:WeatherCandidate,clock:()->Long={System.currentTimeMillis()})=decodeForecast(fetch(forecastUrl(candidate)),clock())

    internal fun decodeCandidates(text:String):List<WeatherCandidate> {
        StrictJson(text).validate();val root=JSONObject(text);val array=root.optJSONArray("results")?:return emptyList()
        require(array.length()<=5)
        return (0 until array.length()).map {index->
            val item=array.getJSONObject(index)
            WeatherCandidate(item.getLong("id"),item.getString("name"),item.optString("admin1",""),item.getString("country"),item.getString("country_code"),
                item.getDouble("latitude"),item.getDouble("longitude"),item.getString("timezone")).also(Environments::validateCandidate)
        }.distinctBy {it.id}
    }

    internal fun decodeForecast(text:String,fetchedAt:Long):WeatherSnapshot {
        StrictJson(text).validate();val root=JSONObject(text);val current=root.getJSONObject("current");val daily=root.getJSONObject("daily")
        fun first(name:String)=daily.getJSONArray(name).also {require(it.length() in 1..3)}.getDouble(0)
        fun firstLong(name:String)=daily.getJSONArray(name).also {require(it.length() in 1..3)}.getLong(0)
        return WeatherSnapshot(current.getString("time"),current.getDouble("temperature_2m"),current.getDouble("apparent_temperature"),current.getLong("relative_humidity_2m"),
            current.getDouble("precipitation"),current.getLong("weather_code"),current.getDouble("wind_speed_10m"),first("temperature_2m_min"),first("temperature_2m_max"),firstLong("precipitation_probability_max"),fetchedAt)
            .also(Environments::validateSnapshot)
    }

    private suspend fun fetch(url:HttpUrl):String {
        require((url.host==geocodingEndpoint.host&&url.port==geocodingEndpoint.port&&url.encodedPath==geocodingEndpoint.encodedPath)||(url.host==forecastEndpoint.host&&url.port==forecastEndpoint.port&&url.encodedPath==forecastEndpoint.encodedPath))
        val client=baseClient.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url(url).header("Accept","application/json").get().build())
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {
                    if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure(if(e is SocketTimeoutException||e.message=="timeout")"TIMEOUT" else "WEATHER_NETWORK"))
                }
                override fun onResponse(call:Call,response:Response) {
                    response.use {
                        try {
                            if(!response.isSuccessful)throw ChatFailure(when(response.code){in 300..399->"REDIRECT";429->"RATE_LIMIT";in 500..599->"SERVER";else->"WEATHER_NETWORK"})
                            val body=response.body?:throw ChatFailure("WEATHER_PROTOCOL")
                            if(body.contentType()?.let {it.type!="application"||it.subtype!="json"}!=false)throw ChatFailure("WEATHER_PROTOCOL")
                            if(body.contentLength()>Environments.RESPONSE_LIMIT)throw ChatFailure("WEATHER_LIMIT")
                            val input=body.byteStream();val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                            while(true){val n=input.read(buffer);if(n<0)break;if(n==0)continue;out.write(buffer,0,n);if(out.size()>Environments.RESPONSE_LIMIT)throw ChatFailure("WEATHER_LIMIT")}
                            val text=out.toByteArray().toString(Charsets.UTF_8);StrictJson(text).validate()
                            if(!continuation.isCancelled)continuation.resume(text)
                        } catch(e:ChatFailure){if(!continuation.isCancelled)continuation.resumeWithException(e)}
                        catch(e:SocketTimeoutException){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("TIMEOUT"))}
                        catch(_:IOException){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure(if(call.isCanceled())"TIMEOUT" else "WEATHER_NETWORK"))}
                        catch(_:Exception){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("WEATHER_PROTOCOL"))}
                        catch(_:OutOfMemoryError){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("WEATHER_LIMIT"))}
                    }
                }
            })
        }
    }

    private fun validateEndpoint(url:HttpUrl,path:String) {
        require(url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.query==null&&url.fragment==null&&url.encodedPath==path)
    }
}
