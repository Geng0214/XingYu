package com.ai.assistance.operit.data.model

data class OnboardingProgress(val configs:Int,val roles:Int,val chats:Int,val completedReplies:Int) {
    init {require(configs>=0&&roles>=0&&chats>=0&&completedReplies>=0)}
    val configured get()=configs>0
    val roleReady get()=roles>0
    val conversationReady get()=chats>0
    val firstReplyReady get()=completedReplies>0
    val done get()=configured&&roleReady&&conversationReady&&firstReplyReady
    val completedSteps get()=listOf(configured,roleReady,conversationReady,firstReplyReady).count {it}
}

enum class RecoveryRoute { CONNECTIONS, HELP }

data class DiagnosticReport(val createdAt:Long,val errorCode:String,val category:String,val appVersion:String,val androidApi:Int,
    val databaseSchema:Int,val configs:Int,val roles:Int,val conversations:Int,val activeRequest:Boolean,val providerKinds:List<String>) {
    fun render():String=buildString {
        appendLine("星语脱敏诊断")
        appendLine("time=$createdAt")
        appendLine("version=$appVersion")
        appendLine("androidApi=$androidApi")
        appendLine("databaseSchema=$databaseSchema")
        appendLine("error=$errorCode")
        appendLine("category=$category")
        appendLine("configs=$configs roles=$roles conversations=$conversations")
        appendLine("activeRequest=$activeRequest")
        append("providers=").append(providerKinds.joinToString(","))
    }
}

object SupportDiagnostics {
    private val connectionCodes=setOf("AUTH","CREDENTIAL","REDIRECT","REQUEST","EMPTY","PROTOCOL")
    private val networkCodes=setOf("NETWORK","TIMEOUT","RATE_LIMIT","SERVER","TRUNCATED")
    private val storageCodes=setOf("STORAGE","ACCESS","INTERRUPTED")
    fun route(code:String)=if(normalize(code) in connectionCodes)RecoveryRoute.CONNECTIONS else RecoveryRoute.HELP
    fun create(code:String,createdAt:Long,appVersion:String,androidApi:Int,configs:Int,roles:Int,conversations:Int,
        activeRequest:Boolean,providers:List<String>):DiagnosticReport {
        val safe=normalize(code)
        val category=when(safe){in connectionCodes->"connection";in networkCodes->"network";in storageCodes->"storage";"NONE"->"status";else->"operation"}
        val kinds=providers.map {when(it){"OpenAI 兼容"->"openai-compatible";"DeepSeek"->"deepseek";"硅基流动"->"siliconflow";else->"other"}}.distinct().sorted()
        return DiagnosticReport(createdAt,safe,category,appVersion.take(32),androidApi.coerceIn(1,999),10,
            configs.coerceAtLeast(0),roles.coerceAtLeast(0),conversations.coerceAtLeast(0),activeRequest,kinds)
    }
    private fun normalize(code:String)=code.takeIf {it.length in 1..32&&it.all {c->c in 'A'..'Z'||c=='_'||c in '0'..'9'}}?:"UNKNOWN"
}
