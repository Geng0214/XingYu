package com.ai.assistance.operit.data.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import com.ai.assistance.operit.data.model.ChatFailure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class PreparedWallpaper(val bytes: ByteArray, val bitmap: Bitmap)
data class PreparedMessageImage(val bytes:ByteArray,val bitmap:Bitmap,val width:Int,val height:Int,val sha256:String)

object LocalWallpaper {
    const val MAX_INPUT=20*1024*1024
    const val MAX_STORED=1536*1024
    fun prepare(input: InputStream, maxEdge: Int=1600, maxStored:Int=MAX_STORED,allowedMimeTypes:Set<String> = setOf("image/jpeg","image/png","image/webp")): PreparedWallpaper = try {
        val stream=ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val n=input.read(buffer,0,minOf(buffer.size,MAX_INPUT+1-stream.size()))
            if(n<0)break
            if(n==0)continue
            stream.write(buffer,0,n)
            require(stream.size()<=MAX_INPUT)
        }
        val data=stream.toByteArray()
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(data,0,data.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=40_000_000)
        require(bounds.outMimeType in allowedMimeTypes)
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge)sample*=2
        var bitmap=BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply { inSampleSize=sample }) ?: throw ChatFailure("IMAGE")
        if(bounds.outMimeType=="image/jpeg") {
            val orientation=ExifInterface(ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            val matrix=Matrix()
            when(orientation) {
                2->matrix.setScale(-1f,1f)
                3->matrix.setRotate(180f)
                4->matrix.setScale(1f,-1f)
                5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)}
                6->matrix.setRotate(90f)
                7->{matrix.setRotate(270f);matrix.postScale(-1f,1f)}
                8->matrix.setRotate(270f)
            }
            if(!matrix.isIdentity) {
                val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
                if(rotated!==bitmap)bitmap.recycle()
                bitmap=rotated
            }
        }
        // Re-encode pixels only: no EXIF/GPS, filename, URI, or original file is retained.
        val normalized=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
        Canvas(normalized).apply { drawColor(Color.WHITE);drawBitmap(bitmap,0f,0f,null) }
        bitmap.recycle()
        val encoded=ByteArrayOutputStream()
        check(normalized.compress(Bitmap.CompressFormat.JPEG,85,encoded))
        require(encoded.size()<=maxStored)
        PreparedWallpaper(encoded.toByteArray(),normalized)
    } catch(_: Exception) { throw ChatFailure("IMAGE") }
      catch(_: OutOfMemoryError) { throw ChatFailure("IMAGE") }

    fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: throw ChatFailure("IMAGE")

    fun portrait(input: InputStream): PreparedWallpaper = try {
        val source=prepare(input,256).bitmap
        val side=minOf(source.width,source.height)
        val cropped=Bitmap.createBitmap(source,(source.width-side)/2,(source.height-side)/2,side,side)
        val thumbnail=Bitmap.createScaledBitmap(cropped,128,128,true)
        if(cropped!==source && cropped!==thumbnail)cropped.recycle()
        if(source!==thumbnail)source.recycle()
        val output=ByteArrayOutputStream()
        check(thumbnail.compress(Bitmap.CompressFormat.JPEG,90,output))
        require(output.size()<=65536)
        PreparedWallpaper(output.toByteArray(),thumbnail)
    } catch(_:Exception) { throw ChatFailure("IMAGE") }
      catch(_:OutOfMemoryError) { throw ChatFailure("IMAGE") }

    private fun messageLike(input:InputStream,maxEdge:Int,minEdge:Int,maxStored:Int,error:String,allowGif:Boolean=false):PreparedMessageImage=try {
        val types=if(allowGif)setOf("image/jpeg","image/png","image/webp","image/gif")else setOf("image/jpeg","image/png","image/webp")
        val prepared=prepare(input,maxEdge,MAX_INPUT,types)
        var bitmap=prepared.bitmap
        fun encode(source:Bitmap,quality:Int)=ByteArrayOutputStream().also {out->check(source.compress(Bitmap.CompressFormat.JPEG,quality,out))}.toByteArray()
        var bytes=prepared.bytes
        for(quality in listOf(82,72,60,48)) {
            if(bytes.size<=maxStored)break
            bytes=encode(bitmap,quality)
        }
        while(bytes.size>maxStored&&maxOf(bitmap.width,bitmap.height)>minEdge) {
            val target=(maxOf(bitmap.width,bitmap.height)*0.8f).toInt().coerceAtLeast(minEdge)
            val ratio=target.toFloat()/maxOf(bitmap.width,bitmap.height)
            val scaled=Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)
            if(scaled!==bitmap)bitmap.recycle()
            bitmap=scaled;bytes=encode(bitmap,60)
        }
        if(bytes.size>maxStored)bytes=encode(bitmap,40)
        require(bytes.isNotEmpty()&&bytes.size<=maxStored)
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        PreparedMessageImage(bytes,bitmap,bitmap.width,bitmap.height,sha)
    }catch(_:Exception){throw ChatFailure(error)}
     catch(_:OutOfMemoryError){throw ChatFailure(error)}

    fun message(input:InputStream)=messageLike(input,com.ai.assistance.operit.data.model.MessageImages.MAX_EDGE,512,
        com.ai.assistance.operit.data.model.MessageImages.MAX_STORED,"IMAGE")

    fun sticker(input:InputStream)=messageLike(input,com.ai.assistance.operit.data.model.StickerAssets.MAX_EDGE,384,
        com.ai.assistance.operit.data.model.StickerAssets.MAX_STORED,"STICKER_IMAGE",allowGif=true)

    fun generated(input:InputStream)=messageLike(input,com.ai.assistance.operit.data.model.ImageGenerations.MAX_EDGE,512,
        com.ai.assistance.operit.data.model.ImageGenerations.MAX_STORED,"IMAGE_GENERATION_IMAGE")
}
