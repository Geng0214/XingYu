package com.ai.assistance.operit.api.audio

import android.content.Context
import android.content.Intent
import android.media.*
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class MusicImport(val track:MusicTrack,val relink:MusicTrack?=null)

class LocalMusicFiles(private val context:Context,private val db:AppDatabase) {
    private val mutex=Mutex()
    private val resolver=context.contentResolver
    suspend fun preview(scope:RoleAccessScope,uri:Uri,relink:MusicTrack?,kind:String):MusicImport {
        val source=AndroidMediaSource.open(resolver,uri,kind)
        val retriever=MediaMetadataRetriever()
        try {
            val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(!it.moveToFirst())throw ChatFailure("MUSIC_FORMAT")
                it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }?:throw ChatFailure("MUSIC_FORMAT")
            val hash=source.fingerprint.sha256;val byteCount=source.fingerprint.size
            retriever.setDataSource(source)
            val hasVideo=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)=="yes"
            if((kind=="video")!=hasVideo||(kind=="audio"&&retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)!="yes"))throw ChatFailure("MUSIC_FORMAT")
            val rotation=if(hasVideo)retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?:throw ChatFailure("MUSIC_FORMAT") else 0
            val width=if(hasVideo)retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:throw ChatFailure("MUSIC_FORMAT") else null
            val height=if(hasVideo)retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:throw ChatFailure("MUSIC_FORMAT") else null
            val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:throw ChatFailure("MUSIC_FORMAT")
            val containerMime=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?:throw ChatFailure("MUSIC_FORMAT")
            // A verified audio-only MP4 container is an M4A track, regardless of its container label.
            val mime=if(kind=="audio"&&containerMime=="video/mp4")"audio/mp4" else containerMime
            val track=MusicTrack(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,name,mime,duration,byteCount,hash,uri.toString(),mediaKind=kind,videoWidth=if(rotation in setOf(90,270))height else width,videoHeight=if(rotation in setOf(90,270))width else height)
            LocalMusic.validate(track)
            if(relink!=null&&(relink.mediaKind!=kind||relink.roleId!=scope.roleId||relink.spaceId!=scope.spaceId||relink.sha256!=hash||relink.byteCount!=byteCount||relink.durationMs!=duration))throw ChatFailure("MUSIC_CHANGED")
            return MusicImport(track,relink)
        }catch(e:CancellationException){throw e}catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("MUSIC_FORMAT")}
        finally {try{retriever.release()}finally{source.close()}}
    }
    suspend fun confirm(preview:MusicImport)=mutex.withLock {
        val uri=Uri.parse(requireNotNull(preview.track.sourceUri))
        val held=resolver.persistedUriPermissions.any {it.uri==uri&&it.isReadPermission}
        try {
            resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            load(preview.track).close()
            val repository=MusicRepository(db)
            if(preview.relink==null)repository.add(preview.track)else repository.relink(preview.relink,uri.toString())
        }catch(e:Exception) {
            withContext(NonCancellable){if(!held&&uri.toString() !in db.music().sources()&&resolver.persistedUriPermissions.any {it.uri==uri&&it.isReadPermission})resolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            throw e
        }
        pruneUnlocked()
    }
    suspend fun load(track:MusicTrack):AndroidMediaSource {
        LocalMusic.validate(track)
        val uri=track.sourceUri?:throw ChatFailure("MUSIC_SOURCE")
        val source=AndroidMediaSource.open(resolver,Uri.parse(uri),track.mediaKind)
        if(source.fingerprint.size!=track.byteCount||source.fingerprint.sha256!=track.sha256){source.close();throw ChatFailure("MUSIC_CHANGED")}
        return source
    }
    suspend fun delete(track:MusicTrack)=mutex.withLock {MusicRepository(db).delete(track);pruneUnlocked()}
    suspend fun prune()=mutex.withLock {pruneUnlocked()}
    private suspend fun pruneUnlocked() {
        val used=db.music().sources().toSet()
        resolver.persistedUriPermissions.filter {it.isReadPermission&&it.uri.toString() !in used}.forEach {
            resolver.releasePersistableUriPermission(it.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

class LocalMusicPlayer(private val context:Context) {
    suspend fun play(source:AndroidMediaSource,startMs:Long,onPosition:suspend (Long)->Unit,surface:android.view.Surface?=null):Boolean=withContext(Dispatchers.Main.immediate) {
        if(android.os.Build.VERSION.SDK_INT<28)
            return@withContext LegacyLocalMediaPlayer(context).play(source,startMs,onPosition,surface)
        val player=MediaPlayer()
        val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val prepared=CompletableDeferred<Unit>();val ended=CompletableDeferred<Unit>();val seeked=CompletableDeferred<Unit>()
        var released=false
        var hasAudio=false
        var routeListener:AudioRouting.OnRoutingChangedListener?=null
        fun releasePlayer() {
            if(released)return
            released=true
            try {if(android.os.Build.VERSION.SDK_INT>=28)routeListener?.let {player.removeOnRoutingChangedListener(it)}}finally {player.release()}
        }
        val audioSession=AndroidMediaAudioSession(context,attributes,::releasePlayer)
        fun observeRoute() {
            // API 26–27 media uses its own observed AudioTrack and video decoder above.
            if(android.os.Build.VERSION.SDK_INT>=28&&hasAudio) {
                val routed=if(android.os.Build.VERSION.SDK_INT>=36)player.routedDevices else listOfNotNull(player.routedDevice)
                audioSession.observe(routed.map {CommunicationDevice(it.id,it.type,"")})
            }
        }
        try {
            audioSession.install()
            return@withContext audioSession.whileHealthy {
            if(source.kind=="video") {
                if(surface?.isValid!=true)throw ChatFailure("VIDEO_SURFACE")
                player.setSurface(surface)
            }
            player.setAudioAttributes(attributes)
            player.setOnPreparedListener {prepared.complete(Unit)}
            player.setOnCompletionListener {ended.complete(Unit)}
            player.setOnSeekCompleteListener {seeked.complete(Unit)}
            player.setOnErrorListener {_,_,_->
                val error=ChatFailure(source.failure?:"MUSIC_PLAYBACK")
                prepared.completeExceptionally(error);seeked.completeExceptionally(error);ended.completeExceptionally(error);true
            }
            player.setDataSource(source);player.prepareAsync()
            withTimeout(15_000){prepared.await()}
            hasAudio=player.trackInfo.any {it.trackType==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO}
            if(startMs>0){player.seekTo(startMs.toInt());withTimeout(10_000){seeked.await()}}
            audioSession.requestFocus()
            if(android.os.Build.VERSION.SDK_INT>=28&&hasAudio)routeListener=AudioRouting.OnRoutingChangedListener {
                if(!released&&!ended.isCompleted)try {observeRoute()}catch(_:Exception){audioSession.fail("MUSIC_ROUTE_CHANGED")}
            }.also {player.addOnRoutingChangedListener(it,android.os.Handler(android.os.Looper.getMainLooper()))}
            player.start()
            val routeDeadline=android.os.SystemClock.elapsedRealtime()+5_000
            while(!ended.isCompleted) {
                audioSession.requireHealthy();observeRoute()
                if(android.os.Build.VERSION.SDK_INT>=28&&hasAudio&&!audioSession.routeReady()&&android.os.SystemClock.elapsedRealtime()>=routeDeadline)throw ChatFailure("MUSIC_ROUTE_UNAVAILABLE")
                onPosition(player.currentPosition.toLong())
                delay(250)
            }
            audioSession.requireHealthy();ended.await()
            if(android.os.Build.VERSION.SDK_INT>=28&&hasAudio&&!audioSession.routeReady())throw ChatFailure("MUSIC_ROUTE_UNAVAILABLE")
            onPosition(player.duration.toLong());true
            }
        }finally {
            try {releasePlayer()}finally {try {audioSession.close()}finally {source.close()}}
        }
    }
}
