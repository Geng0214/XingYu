package com.ai.assistance.operit.reminder

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.PlannerEntry
import com.ai.assistance.operit.data.model.PlannerRecords
import com.ai.assistance.operit.data.security.CredentialVault
import com.ai.assistance.operit.ui.main.MainActivity
import kotlinx.coroutines.*

object ReminderScheduler {
    const val ACTION_REMIND="cn.localfirst.companion.action.PLANNER_REMINDER"
    private const val CHANNEL="local_planner_reminders"
    private const val EXTRA_ID="entry_id"

    internal fun notificationChannelEnabled(context:Context):Boolean? {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return null
        val channel=context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        return channel?.importance?.let {it!=NotificationManager.IMPORTANCE_NONE}
    }

    fun schedule(context:Context,entry:PlannerEntry,now:Long=System.currentTimeMillis()) {
        cancel(context,entry.id)
        val trigger=PlannerRecords.reminderAt(entry,now)?:return
        val manager=context.getSystemService(AlarmManager::class.java)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,requireNotNull(pending(context,entry.id,PendingIntent.FLAG_UPDATE_CURRENT)))
    }

    fun cancel(context:Context,id:String) {
        val intent=pending(context,id,PendingIntent.FLAG_NO_CREATE)
        if(intent!=null){context.getSystemService(AlarmManager::class.java).cancel(intent);intent.cancel()}
    }

    fun sync(context:Context,entries:List<PlannerEntry>,now:Long=System.currentTimeMillis())=entries.forEach {schedule(context,it,now)}

    private fun pending(context:Context,id:String,mode:Int):PendingIntent? {
        val intent=Intent(context,PlannerReminderReceiver::class.java).setAction(ACTION_REMIND)
            .setData(Uri.Builder().scheme("local-companion").authority("reminder").appendPath(id).build()).putExtra(EXTRA_ID,id)
        return PendingIntent.getBroadcast(context,0,intent,mode or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun entryId(intent:Intent)=intent.getStringExtra(EXTRA_ID)?.takeIf {it.length in 1..80}

    internal fun notify(context:Context,id:String) {
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val notifications=context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"本机日程提醒",NotificationManager.IMPORTANCE_DEFAULT).apply {
            description="仅显示用户明确设置的本机提醒；通知不显示记录正文。"
            lockscreenVisibility=android.app.Notification.VISIBILITY_SECRET
        })
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_xingyu_notification).setContentTitle("星语提醒")
            .setContentText("你有一项已设置的本机提醒，解锁应用查看。")
            .setVisibility(Notification.VISIBILITY_SECRET).setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(open).build()
        notifications.notify(id.hashCode() and Int.MAX_VALUE,notification)
    }
}

class PlannerReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        when(intent.action) {
            ReminderScheduler.ACTION_REMIND-> {
                val id=ReminderScheduler.entryId(intent)?:return
                ReminderScheduler.notify(context,id)
                refresh(context,id)
            }
            Intent.ACTION_BOOT_COMPLETED->refresh(context,null)
        }
    }

    private fun refresh(context:Context,id:String?) {
        val pending=goAsync();val app=context.applicationContext
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val db=AppDatabase.open(app,CredentialVault(app))
                try {
                    if(id==null)ReminderScheduler.sync(app,db.planner().allReminderEntries())
                    else db.planner().reminderEntry(id)?.takeIf {it.kind=="anniversary"}?.let {ReminderScheduler.schedule(app,it,System.currentTimeMillis()+1)}
                } finally {db.close()}
            } catch(_:Exception) {
                // A missing or locked database is never replaced or cleared from a background receiver.
            } finally {pending.finish()}
        }
    }
}
