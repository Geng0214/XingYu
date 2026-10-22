package com.ai.assistance.operit.proactive

import android.Manifest
import android.app.*
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.ModelGenerationGate
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.api.chat.llmprovider.ChatStreamEvent
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIProvider
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ProactiveRepository
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.CredentialVault
import com.ai.assistance.operit.ui.main.MainActivity
import kotlinx.coroutines.*
import java.time.ZoneId

class ProactivePreferences(context:Context) {
    private val values=context.applicationContext.getSharedPreferences("proactive-control",Context.MODE_PRIVATE)
    fun enabled()=values.getBoolean("master-enabled",false)
    fun dailyLimit()=values.getInt("global-daily-limit",3).coerceIn(1,10)
    fun set(enabled:Boolean,dailyLimit:Int) {require(dailyLimit in 1..10);values.edit().putBoolean("master-enabled",enabled).putInt("global-daily-limit",dailyLimit).apply()}
}

object ProactiveRuntime {
    private val lock=Any();private var active:Pair<RoleAccessScope,Job>?=null
    fun register(scope:RoleAccessScope,job:Job):Boolean=synchronized(lock){if(active!=null)false else {active=scope to job;true}}
    fun bind(scope:RoleAccessScope,job:Job)=synchronized(lock){if(active?.second===job)active=scope to job}
    fun unregister(job:Job)=synchronized(lock){if(active?.second===job)active=null}
    fun cancel(scope:RoleAccessScope?=null)=synchronized(lock){active?.takeIf {scope==null||it.first==scope||it.first.roleId=="*"}?.second?.cancel()}
}

object ProactiveScheduler {
    private const val JOB_ID=0x434F4D50
    private const val CHANNEL="local_proactive_messages"
    internal fun notificationChannelEnabled(context:Context):Boolean? {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return null
        val channel=context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        return channel?.importance?.let {it!=NotificationManager.IMPORTANCE_NONE}
    }
    fun sync(context:Context,enabled:Boolean) {
        val scheduler=context.getSystemService(JobScheduler::class.java)
        if(!enabled){scheduler.cancel(JOB_ID);ProactiveRuntime.cancel();return}
        val info=JobInfo.Builder(JOB_ID,ComponentName(context,ProactiveJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true).setPersisted(true)
            .setPeriodic(60L*60L*1000L,15L*60L*1000L).build()
        if(scheduler.schedule(info)!=JobScheduler.RESULT_SUCCESS)throw ChatFailure("STORAGE")
    }
    fun hasNotificationPermission(context:Context)=Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    fun notify(context:Context,preview:Boolean=false) {
        if(!hasNotificationPermission(context))throw ChatFailure("PROACTIVE_DISABLED")
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"主动消息",NotificationManager.IMPORTANCE_DEFAULT).apply {
            description="仅在明确开启的消息频率内提示；锁屏不显示角色或消息正文。"
            lockscreenVisibility=Notification.VISIBILITY_SECRET
        })
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_xingyu_notification).setContentTitle("星语有新消息")
            .setContentText("解锁应用查看").setVisibility(Notification.VISIBILITY_SECRET).setCategory(Notification.CATEGORY_MESSAGE).setAutoCancel(true).setContentIntent(open).build()
        manager.notify(if(preview)JOB_ID+1 else JOB_ID,notification)
    }
}

class ProactiveEngine(private val context:Context) {
    suspend fun runOnce():Boolean {
        val preferences=ProactivePreferences(context)
        if(!preferences.enabled()||!ProactiveScheduler.hasNotificationPermission(context)||ModelGenerationGate.busy())return false
        val db=AppDatabase.open(context,CredentialVault(context));val owner="proactive:${System.nanoTime()}"
        if(!ModelGenerationGate.acquire(owner)){db.close();return false}
        try {
            db.recoverInterruptedRequests()
            val repository=ProactiveRepository(db);val now=System.currentTimeMillis().coerceAtLeast(0);val zone=ZoneId.systemDefault()
            val candidates=db.proactive().enabledSettings()
            var prepared:ProactiveRepository.Prepared?=null
            for(setting in candidates) {
                try {prepared=repository.prepare(setting,preferences.dailyLimit(),now,zone);break}
                catch(e:ChatFailure){if(e.code!="PROACTIVE_LIMIT"&&e.code!="PROACTIVE_CONFIG")throw e}
            }
            val request=prepared?:return false
            currentCoroutineContext()[Job]?.let {ProactiveRuntime.bind(request.snapshot.scope,it)}
            val started=System.nanoTime()/1_000_000;var first=-1L;var persisted=started;val content=StringBuilder()
            var networkStarted=false
            suspend fun update(state:MessageState,error:String="")=RoomRequestStore(db).update(request.snapshot,content.toString(),state,error,first,System.nanoTime()/1_000_000-started)
            try {
                if(!preferences.enabled()||!ProactiveScheduler.hasNotificationPermission(context)){repository.discardBeforeNetwork(request);return false}
                update(MessageState.WAITING)
                val key=CredentialVault(context).read(request.snapshot.config.credentialRef,request.snapshot.config.endpoint)
                if(!preferences.enabled()||!ProactiveScheduler.hasNotificationPermission(context)){repository.discardBeforeNetwork(request);return false}
                currentCoroutineContext().ensureActive()
                networkStarted=true
                OpenAIProvider().sendEvents(request.snapshot,key).collect {event->when(event){
                    is ChatStreamEvent.Text->{content.append(event.value);val clock=System.nanoTime()/1_000_000;if(first<0)first=clock-started;if(clock-persisted>=80){update(MessageState.GENERATING);persisted=clock}}
                    is ChatStreamEvent.Reasoning->Unit
                    is ChatStreamEvent.RoundEnd->Unit
                }}
                repository.finish(request,content.toString(),MessageState.COMPLETE,"",first,System.nanoTime()/1_000_000-started,false)
                try {
                    ProactiveScheduler.notify(context)
                    if(db.proactive().markNotified(request.request.requestId,request.request.roleId,request.request.spaceId)!=1)throw ChatFailure("STORAGE")
                }catch(_:Exception) {
                    // The completed encrypted message remains available even if Android rejects a late notification.
                }
            }catch(e:CancellationException){withContext(NonCancellable){if(networkStarted)repository.finish(request,content.toString(),MessageState.CANCELLED,"",first,System.nanoTime()/1_000_000-started,false)else repository.discardBeforeNetwork(request)};throw e}
            catch(e:Exception){val code=(e as? ChatFailure)?.code?:"STORAGE";repository.finish(request,content.toString(),MessageState.FAILED,code,first,System.nanoTime()/1_000_000-started,false)}
            return true
        } finally {ModelGenerationGate.release(owner);db.close()}
    }
}

class ProactiveJobService:JobService() {
    @Volatile private var task:Job?=null
    override fun onStartJob(params:JobParameters):Boolean {
        val created=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch(start=CoroutineStart.LAZY) {
            val current=requireNotNull(currentCoroutineContext()[Job])
            try {ProactiveEngine(applicationContext).runOnce()}
            finally {
                val finish=task===current
                if(finish)task=null
                if(finish)jobFinished(params,false)
                ProactiveRuntime.unregister(current)
            }
        }
        task=created
        if(!ProactiveRuntime.register(RoleAccessScope("*","*"),created)){created.cancel();task=null;return false}
        created.start()
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {val running=task;task=null;running?.cancel();return false}
}
