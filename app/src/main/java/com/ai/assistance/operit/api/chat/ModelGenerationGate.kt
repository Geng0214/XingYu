package com.ai.assistance.operit.api.chat

import java.util.concurrent.atomic.AtomicReference

/** Process-wide gate: foreground chat and background proactive generation may never overlap. */
object ModelGenerationGate {
    private val owner=AtomicReference<String?>(null)
    fun acquire(id:String):Boolean=owner.compareAndSet(null,id)
    fun release(id:String) {owner.compareAndSet(id,null)}
    fun busy():Boolean=owner.get()!=null
}
