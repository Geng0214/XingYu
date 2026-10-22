package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.RequestSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface ChatStreamEvent {
    data class Text(val value:String):ChatStreamEvent
    data object Reasoning:ChatStreamEvent
    data object RoundEnd:ChatStreamEvent
}

/** Text, reasoning progress and completion are separate streaming events. */
interface AIService {
    fun sendMessage(request: RequestSnapshot, key: String): Flow<String>
    fun sendEvents(request:RequestSnapshot,key:String):Flow<ChatStreamEvent> = flow {
        sendMessage(request,key).collect {emit(ChatStreamEvent.Text(it))}
        emit(ChatStreamEvent.RoundEnd)
    }
}
