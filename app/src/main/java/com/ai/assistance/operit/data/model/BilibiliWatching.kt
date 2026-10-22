package com.ai.assistance.operit.data.model

import androidx.room.*
import java.net.URI

data class BilibiliVideo(val videoId:String,val part:Int=1) {
    init {require(isId(videoId)&&part in 1..1000)}
    fun pageUrl()="https://www.bilibili.com/video/$videoId/?p=$part"
    fun playerUrl(positionMs:Long):String {
        require(positionMs in 0..LocalMusic.MAX_DURATION_MS)
        val id=if(videoId.startsWith("BV"))"bvid=$videoId" else "aid=${videoId.substring(2)}"
        return "https://player.bilibili.com/player.html?$id&p=$part&autoplay=0&danmaku=0&t=${positionMs/1000}"
    }
    companion object {
        fun isId(s:String)=s.matches(Regex("BV[1-9A-HJ-NP-Za-km-z]{10}"))||
            (s.matches(Regex("av[1-9][0-9]{0,15}"))&&s.substring(2).toLong()<=9_007_199_254_740_991L)
        fun parse(text:String):BilibiliVideo {
            try {
                val value=text.trim();require(value.length<=2048)
                if(isId(value))return BilibiliVideo(value)
                val uri=URI(value)
                require(uri.scheme=="https"&&uri.host in setOf("www.bilibili.com","bilibili.com","m.bilibili.com"))
                require(uri.rawUserInfo==null&&uri.port==-1&&uri.rawFragment==null)
                val path=uri.rawPath.removeSuffix("/");require(path.startsWith("/video/"))
                val id=path.removePrefix("/video/");require(isId(id))
                val parts=uri.rawQuery?.split('&')?.filter {it.substringBefore('=')=="p"}.orEmpty()
                require(parts.size<=1)
                val part=if(parts.isEmpty())1 else parts.single().substringAfter('=',"").toInt()
                return BilibiliVideo(id,part)
            }catch(_:Exception){throw ChatFailure("BILI_LINK")}
        }
    }
}

@Entity(tableName="bilibili_watches",foreignKeys=[
    ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ChatEntity::class,parentColumns=["id"],childColumns=["chatId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("roleId"),Index("chatId"),Index(value=["roleId","spaceId"])])
data class BilibiliWatch(@PrimaryKey val id:String,val roleId:String,val spaceId:String,val chatId:String,
    val title:String,val videoId:String,val part:Int=1,val positionMs:Long=0,val durationMs:Long?=null,
    val note:String="",val playbackToken:String?=null,val version:Long=1,
    val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=createdAt) {
    fun video()=BilibiliVideo(videoId,part)
    fun scope()=RoleAccessScope(roleId,spaceId)
}

object BilibiliWatching {
    fun validate(w:BilibiliWatch) {
        require(listOf(w.id,w.roleId,w.spaceId,w.chatId).all {it.isNotBlank()})
        w.video()
        require(w.title.isNotBlank()&&w.title.length<=160&&w.title.none {it.isISOControl()})
        require(w.positionMs in 0..LocalMusic.MAX_DURATION_MS)
        require(w.durationMs==null||(w.durationMs in 1..LocalMusic.MAX_DURATION_MS&&w.positionMs<=w.durationMs))
        require(w.note.length<=SharedActivities.NOTE_LIMIT&&'\u0000' !in w.note)
        require(w.playbackToken==null||(w.playbackToken.isNotBlank()&&w.playbackToken.length<=128))
        require(w.version in 1 until Long.MAX_VALUE&&w.createdAt>=0&&w.updatedAt>=w.createdAt)
    }
    fun position(seconds:Double,duration:Double):Pair<Long,Long> {
        if(!seconds.isFinite()||!duration.isFinite()||seconds<0||duration<=0||seconds>duration||duration*1000>LocalMusic.MAX_DURATION_MS)
            throw ChatFailure("BILI_PROGRESS")
        return (seconds*1000).toLong() to (duration*1000).toLong()
    }
    fun summary(w:BilibiliWatch):String {
        validate(w)
        return "我们的 B 站观看记录\n作品：${w.title}\n链接：${w.video().pageUrl()}\n上次记录进度：${LocalMusic.time(w.positionMs)}"+
            (w.durationMs?.let {" / ${LocalMusic.time(it)}"}?:"")+if(w.note.isBlank())"" else "\n我的感想：${w.note}"
    }
}
