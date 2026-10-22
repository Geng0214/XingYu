package com.ai.assistance.operit.data.security

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

enum class PrivacyPurpose { ENABLE, UNLOCK, DISABLE }
data class PrivacyTicket(val id:String,val purpose:PrivacyPurpose)
data class PrivacyState(val loaded:Boolean=false,val enabled:Boolean=false,val unlocked:Boolean=false,
    val foreground:Boolean=false,val deviceSecure:Boolean=false,val ticket:PrivacyTicket?=null,
    val saving:Boolean=false,val error:String="",val epoch:Long=0) {
    val canAccess get()=loaded&&error!="STORAGE"&&ticket==null&&!saving&&(!enabled||(unlocked&&foreground&&deviceSecure))
}

/** Only system-authentication callbacks may complete a ticket. Never persist an unlocked session. */
class PrivacyGate(private val accessClosed:()->Unit={}) {
    private val monitor=Any()
    private val mutable=MutableStateFlow(PrivacyState())
    val state=mutable.asStateFlow()
    private fun change(next:PrivacyState) {
        val wasOpen=mutable.value.canAccess
        mutable.value=next
        if(wasOpen&&!next.canAccess)accessClosed()
    }
    fun initialize(enabled:Boolean)=synchronized(monitor) {
        check(!state.value.loaded)
        change(state.value.copy(loaded=true,enabled=enabled,unlocked=false))
    }
    fun storageFailed()=synchronized(monitor){change(state.value.copy(loaded=true,unlocked=false,error="STORAGE",epoch=state.value.epoch+1))}
    fun resumed(secure:Boolean)=synchronized(monitor) {
        val s=state.value
        change(s.copy(foreground=true,deviceSecure=secure,unlocked=s.unlocked&&secure,
            error=if(s.error=="STORAGE")s.error else if(s.enabled&&!secure)"UNAVAILABLE"else if(s.error=="UNAVAILABLE")""else s.error))
    }
    fun stopped()=synchronized(monitor) {
        val s=state.value
        change(s.copy(foreground=false,unlocked=false,epoch=s.epoch+if(s.enabled||s.ticket!=null)1 else 0))
    }
    fun lockNow()=synchronized(monitor) {
        val s=state.value
        if(s.enabled)change(s.copy(unlocked=false,epoch=s.epoch+1))
    }
    fun begin(purpose:PrivacyPurpose):PrivacyTicket?=synchronized(monitor) {
        val s=state.value
        if(!s.loaded||!s.foreground||s.ticket!=null||s.saving||s.error=="STORAGE")return null
        if(!s.deviceSecure){change(s.copy(error="UNAVAILABLE"));return null}
        val allowed=when(purpose){PrivacyPurpose.ENABLE->!s.enabled;PrivacyPurpose.DISABLE->s.enabled&&s.canAccess;PrivacyPurpose.UNLOCK->s.enabled&&!s.canAccess}
        if(!allowed)return null
        val ticket=PrivacyTicket(UUID.randomUUID().toString(),purpose)
        change(s.copy(ticket=ticket,unlocked=false,error="",epoch=s.epoch+1));ticket
    }
    fun cancel(ticketId:String?=null,code:String="CANCELLED")=synchronized(monitor) {
        val s=state.value
        if(s.saving||s.ticket==null||(ticketId!=null&&s.ticket.id!=ticketId))return
        change(s.copy(ticket=null,unlocked=false,error=code,epoch=s.epoch+1))
    }
    suspend fun authenticated(ticketId:String,persist:suspend (Boolean)->Unit):Boolean {
        val captured=synchronized(monitor) {
            val s=state.value
            if(s.ticket?.id!=ticketId||s.saving||!s.foreground||!s.deviceSecure||s.error=="STORAGE")return false
            change(s.copy(saving=true));s
        }
        // Once a settings write begins, finish it atomically even if the Activity leaves. A lifecycle
        // epoch change prevents the completed write from reopening a session in the background.
        return withContext(NonCancellable) {
            val enabled=captured.ticket!!.purpose!=PrivacyPurpose.DISABLE
            try {
                if(captured.ticket.purpose!=PrivacyPurpose.UNLOCK)persist(enabled)
                synchronized(monitor) {
                    val current=state.value
                    change(current.copy(enabled=enabled,ticket=null,saving=false,error=if(current.error=="STORAGE")"STORAGE"else"",
                        unlocked=current.error!="STORAGE"&&current.foreground&&current.deviceSecure&&current.epoch==captured.epoch))
                };true
            }catch(_:Exception){synchronized(monitor){change(state.value.copy(ticket=null,saving=false,unlocked=false,error="STORAGE"))};false}
        }
    }
    fun permit():Long=synchronized(monitor) {if(!state.value.canAccess)throw ChatFailure("LOCKED");state.value.epoch}
    fun requirePermit(epoch:Long)=synchronized(monitor) {if(!state.value.canAccess||state.value.epoch!=epoch)throw ChatFailure("LOCKED")}
    /** Publish foreground-only private state atomically with respect to locking and lifecycle. */
    fun <T> withForegroundPermit(epoch:Long,publish:()->T):T=synchronized(monitor) {
        requirePermit(epoch)
        if(!state.value.foreground)throw ChatFailure("LOCKED")
        publish()
    }
}
