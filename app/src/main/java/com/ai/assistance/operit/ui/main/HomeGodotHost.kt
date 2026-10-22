package com.ai.assistance.operit.ui.main

import android.app.Activity
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import kotlin.math.roundToInt

/** The bridge contains render data only. Room remains the sole authority for the shared world. */
class HomeGodotBridge(engine:Godot):GodotPlugin(engine) {
    @Volatile private var latest=""
    @Volatile var failure:String?=null
        private set
    override fun getPluginName()="XingyuHome"
    fun publish(packet:String){latest=packet}
    @UsedByGodot fun snapshot():String=latest
    @UsedByGodot fun report_failure(message:String){failure=message;android.util.Log.e("XingyuHome",message)}
}

/** The render view stays attached to the activity: detaching Godot's GL view destroys its engine. */
internal class HomeGodotHost(private val owner:Activity):GodotHost {
    private val engine=Godot.getInstance(owner)
    val bridge=HomeGodotBridge(engine)
    private var container:FrameLayout?=null
    var visible by mutableStateOf(false)
        private set
    var requested by mutableStateOf(false)
        private set
    var viewport by mutableStateOf(IntRect.Zero)
        private set
    var reduceMotion by mutableStateOf(readReduceMotion())
        private set
    private var resumed=false
    override fun getActivity()=owner
    override fun getGodot()=engine
    override fun getCommandLine()=listOf("--main-pack","res://home.pck","--rendering-method","gl_compatibility","--disable_godot_splash")
    override fun getHostPlugins(engine:Godot)=setOf<GodotPlugin>(bridge)
    override fun onGodotForceQuit(instance:Godot){owner.runOnUiThread {owner.finish()}}
    fun attach():FrameLayout {
        check(container==null) {"The activity must own exactly one Godot render view"}
        check(engine.initEngine(this,commandLine,getHostPlugins(engine))) {"Godot initialization failed"}
        val created=requireNotNull(engine.onInitRenderView(this,FrameLayout(owner))) {"Godot render view initialization failed"}
        container=created
        syncLifecycle()
        return created
    }
    fun viewport(bounds:IntRect){require(bounds.width>0&&bounds.height>0);viewport=bounds;requested=true}
    // ValueAnimator caches duration scale; an observer can run before that cache updates.
    // Read the requested setting directly so live removal of motion reaches the game immediately.
    private fun readReduceMotion()=Settings.Global.getString(owner.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE)?.toFloat()==0f
    fun animationPreference(){reduceMotion=readReduceMotion()}
    fun visibility(value:Boolean) {
        visible=value
        syncLifecycle()
    }
    fun resumed(value:Boolean){resumed=value;syncLifecycle()}
    private var rendering=false
    private fun syncLifecycle() {
        val render=visible&&resumed&&container!=null
        if(render==rendering)return
        rendering=render
        if(render){engine.onStart(this);engine.onResume(this)}else{engine.onPause(this);engine.onStop(this)}
    }
    fun configuration(config:Configuration){if(container!=null)engine.onConfigurationChanged(config)}
    fun destroy(){if(container!=null)engine.onDestroy(this)}
}

@Composable internal fun HomeGodotSurface(packet:String,modifier:Modifier) {
    val context=LocalContext.current
    val activity=remember(context){generateSequence(context){(it as? android.content.ContextWrapper)?.baseContext}.filterIsInstance<MainActivity>().first()}
    val host=activity.homeEngine
    SideEffect {host.bridge.publish(packet)}
    Box(modifier.onGloballyPositioned {coordinates->
        val size=coordinates.size
        if(size.width>0&&size.height>0) {
            val position=coordinates.positionInWindow()
            val x=position.x.roundToInt();val y=position.y.roundToInt()
            host.viewport(IntRect(x,y,x+size.width,y+size.height))
        }
    })
    DisposableEffect(host) {
        host.visibility(true)
        onDispose {host.visibility(false)}
    }
}

/** A permanent portal in Scaffold's content, below Compose controls and above its background. */
@Composable internal fun HomeGodotLayer(modifier:Modifier) {
    val context=LocalContext.current
    val activity=remember(context){generateSequence(context){(it as? android.content.ContextWrapper)?.baseContext}.filterIsInstance<MainActivity>().first()}
    val host=activity.homeEngine
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var origin by remember {mutableStateOf(IntOffset.Zero)}
    DisposableEffect(host,lifecycle) {
        val observer=LifecycleEventObserver {_,_->host.resumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
        host.resumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        lifecycle.addObserver(observer)
        val preferenceObserver=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean){host.animationPreference()}
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,preferenceObserver)
        onDispose {context.contentResolver.unregisterContentObserver(preferenceObserver);lifecycle.removeObserver(observer);host.resumed(false)}
    }
    Layout(modifier=modifier.onGloballyPositioned {coordinates->
        val position=coordinates.positionInWindow();origin=IntOffset(position.x.roundToInt(),position.y.roundToInt())
    },content={
        if(host.requested)AndroidView(factory={host.attach()},update={it.visibility=if(host.visible)View.VISIBLE else View.GONE})
    }) {measurables,constraints->
        val bounds=host.viewport
        val view=measurables.firstOrNull()?.measure(Constraints.fixed(bounds.width,bounds.height))
        layout(constraints.maxWidth,constraints.maxHeight){view?.place(bounds.left-origin.x,bounds.top-origin.y)}
    }
}
