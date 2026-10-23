package com.ai.assistance.operit.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ScheduledMessage
import com.ai.assistance.operit.data.security.CredentialVault

object ScheduledMessageScheduler {
    private fun pendingIntent(context:Context,id:String):PendingIntent {
        val intent=Intent(context,ScheduledMessageReceiver::class.java).setData(Uri.parse("local-companion://scheduled/$id"))
        return PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context:Context,item:ScheduledMessage) {
        val alarm=context.getSystemService(AlarmManager::class.java) ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,item.dueAt,pendingIntent(context,item.id))
    }

    fun cancel(context:Context,id:String) {
        val alarm=context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(pendingIntent(context,id))
    }

    /** 重新排定全部待发消息；开机与改表后调用。 */
    suspend fun sync(context:Context) {
        val db=AppDatabase.open(context,CredentialVault(context))
        try {db.scheduled().pending().forEach {schedule(context,it)}} finally {db.close()}
    }
}
