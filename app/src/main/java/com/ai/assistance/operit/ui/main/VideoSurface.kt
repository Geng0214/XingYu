package com.ai.assistance.operit.ui.main

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@Composable
internal fun VideoSurface(width:Int,height:Int,onSurface:(Surface)->Unit,onClosed:(Surface)->Unit) {
    val currentCallback by rememberUpdatedState(onSurface)
    val currentClosed by rememberUpdatedState(onClosed)
    var ownedSurface by remember {mutableStateOf<Surface?>(null)}
    val ratio=width.toFloat()/height
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black),contentAlignment=androidx.compose.ui.Alignment.Center) {
    val videoWidth=minOf(maxWidth,320.dp*ratio)
    AndroidView(modifier=Modifier.size(videoWidth,videoWidth/ratio),factory={context->
        SurfaceView(context).also {view->
            view.setSecure(true)
            view.holder.addCallback(object:SurfaceHolder.Callback {
                override fun surfaceCreated(holder:SurfaceHolder){ownedSurface=holder.surface;currentCallback(holder.surface)}
                override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){currentCallback(holder.surface)}
                override fun surfaceDestroyed(holder:SurfaceHolder){ownedSurface?.let {currentClosed(it)};ownedSurface=null}
            })
        }
    },onRelease={ownedSurface?.let {currentClosed(it)};ownedSurface=null})
    }
}
