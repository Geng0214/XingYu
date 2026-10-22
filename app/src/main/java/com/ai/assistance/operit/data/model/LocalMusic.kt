package com.ai.assistance.operit.data.model

import androidx.room.*
import java.security.MessageDigest

@Entity(tableName="music_tracks",foreignKeys=[ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE)],indices=[Index("roleId"),Index(value=["roleId","spaceId"])])
data class MusicTrack(@PrimaryKey val id:String,val roleId:String,val spaceId:String,val displayName:String,
    val mimeType:String,val durationMs:Long,val byteCount:Long,val sha256:String,val sourceUri:String?,
    val version:Long=1,val createdAt:Long=System.currentTimeMillis(),
    @ColumnInfo(defaultValue="'audio'") val mediaKind:String="audio",val videoWidth:Int?=null,val videoHeight:Int?=null)

@Entity(tableName="music_sessions",foreignKeys=[
    ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ChatEntity::class,parentColumns=["id"],childColumns=["chatId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=MusicTrack::class,parentColumns=["id"],childColumns=["trackId"],onDelete=ForeignKey.NO_ACTION)
],indices=[Index("roleId"),Index("chatId"),Index("trackId"),Index(value=["roleId","spaceId"])])
data class MusicSession(@PrimaryKey val id:String,val roleId:String,val spaceId:String,val chatId:String,val trackId:String,
    val state:String="paused",val positionMs:Long=0,val note:String="",val playbackToken:String?=null,
    val version:Long=1,val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=createdAt)

object LocalMusic {
    const val MAX_BYTES=32*1024*1024
    const val MAX_VIDEO_BYTES=512*1024*1024L
    const val MAX_DURATION_MS=6*60*60*1000L
    const val MAX_TRACKS=64
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun validate(track:MusicTrack) {
        require(track.id.isNotBlank()&&track.roleId.isNotBlank()&&track.spaceId.isNotBlank())
        require(track.displayName.isNotBlank()&&track.displayName.length<=160&&track.displayName.none {it.isISOControl()})
        require(track.mediaKind in setOf("audio","video"))
        require(track.mimeType.startsWith("${track.mediaKind}/")&&track.mimeType.length<=100)
        require(track.durationMs in 1..MAX_DURATION_MS&&track.byteCount in 1..maxBytes(track.mediaKind))
        if(track.mediaKind=="video")require(track.videoWidth in 1..16384&&track.videoHeight in 1..16384)
        else require(track.videoWidth==null&&track.videoHeight==null)
        require(track.sha256.matches(Regex("[a-f0-9]{64}")))
        require(track.sourceUri==null||(track.sourceUri.startsWith("content://")&&track.sourceUri.length<=4096))
        require(track.version in 1 until Long.MAX_VALUE&&track.createdAt>=0)
    }
    fun validate(session:MusicSession,track:MusicTrack) {
        validate(track)
        require(session.id.isNotBlank()&&session.chatId.isNotBlank())
        require(session.roleId==track.roleId&&session.spaceId==track.spaceId&&session.trackId==track.id)
        require(session.state in setOf("playing","paused","completed"))
        require(if(session.state=="playing")!session.playbackToken.isNullOrBlank()&&session.playbackToken.length<=128 else session.playbackToken==null)
        require(session.positionMs in 0..track.durationMs)
        require(session.note.length<=SharedActivities.NOTE_LIMIT&&'\u0000' !in session.note)
        require(session.version in 1 until Long.MAX_VALUE&&session.createdAt>=0&&session.updatedAt>=session.createdAt)
    }
    fun verify(track:MusicTrack,bytes:ByteArray) {
        validate(track)
        if(bytes.size.toLong()!=track.byteCount||hash(bytes)!=track.sha256)throw ChatFailure("MUSIC_CHANGED")
    }
    fun time(ms:Long):String="%d:%02d".format(ms/60000,(ms/1000)%60)
    fun maxBytes(kind:String):Long=when(kind){"audio"->MAX_BYTES.toLong();"video"->MAX_VIDEO_BYTES;else->throw ChatFailure("INPUT")}
    fun summary(session:MusicSession,track:MusicTrack):String {
        validate(session,track)
        val label=if(track.mediaKind=="video")"观看" else "听歌"
        return "我们的${label}记录\n作品：${track.displayName}\n进度：${time(session.positionMs)} / ${time(track.durationMs)}"+
            if(session.note.isBlank())"" else "\n我的感想：${session.note}"
    }
}
