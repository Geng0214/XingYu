package com.ai.assistance.operit.core.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class PendingToolApproval(val id:String,val call:ToolInvocation)

/** An approval is one-time and belongs to a specific call; stale dialogs cannot approve a later request. */
class ToolApprovalGate {
    private val serial=Mutex()
    private val lock=Any()
    private val _pending=MutableStateFlow<PendingToolApproval?>(null)
    val pending=_pending.asStateFlow()
    private var decision:CompletableDeferred<Boolean>?=null
    suspend fun await(call:ToolInvocation):Boolean=serial.withLock {
        val ticket=PendingToolApproval(UUID.randomUUID().toString(),call)
        val answer=CompletableDeferred<Boolean>()
        synchronized(lock){decision=answer;_pending.value=ticket}
        try {answer.await()}finally {
            synchronized(lock){if(_pending.value?.id==ticket.id){_pending.value=null;decision=null}}
        }
    }
    fun answer(id:String,allow:Boolean):Boolean=synchronized(lock) {
        if(_pending.value?.id!=id)false else decision?.complete(allow)==true
    }
}
