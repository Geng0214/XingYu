package com.ai.assistance.operit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledMessageReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        val id=intent.data?.lastPathSegment
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if(intent.action==Intent.ACTION_BOOT_COMPLETED) {
                    ScheduledMessageScheduler.sync(context)
                } else if(id!=null) {
                    ScheduledMessageRunner.run(context,id)
                    ScheduledMessageScheduler.sync(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
