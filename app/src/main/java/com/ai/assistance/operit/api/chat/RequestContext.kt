package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.*

object RequestContext {
    data class Resolved(val turns:List<PromptTurn>,val worldbook:WorldbookSelection,val relationships:RelationshipSelection,
        val memories:MemorySelection=MemorySelection("",emptyList(),0),val environment:EnvironmentSelection=EnvironmentSelection(),val planner:PlannerSelection=PlannerSelection()) {
        val systemInstruction:String get()=turns.filter {it.role=="system"}.joinToString("\n\n"){it.content}
    }
    // The editor preview and every real send/retry use exactly the same system prompt builder.
    fun systemPrompt(role:RoleEntity):String {
        if(role.isRaw())return ""
        val identity=role.prompt+UserPersona.instruction(role)
        return if(role.replyStyle=="short")identity+"\n\n回复格式：像即时聊天一样自然交流。通常回复 1–5 条简短消息，每条独立成段，条与条之间用一个空行分隔。不要加编号或角色名前缀，不要为了分段拆散一句话。需要详细解释或代码时可保持完整。保持 AI 身份，不虚构真实转账、通话或现实行动。"else identity
    }
    fun build(scope: RoleAccessScope, role: RoleEntity, chat: ChatEntity, history: List<MessageEntity>, text: String,
        entries:List<WorldbookEntry> = emptyList(),relationships:List<RelationshipEntry> = emptyList(),memories:List<MemoryEntry> = emptyList()):List<PromptTurn> = resolve(scope,role,chat,history,text,entries,relationships,memories=memories).turns
    fun resolve(scope:RoleAccessScope,role:RoleEntity,chat:ChatEntity,history:List<MessageEntity>,text:String,entries:List<WorldbookEntry>,relationships:List<RelationshipEntry> = emptyList(),
        attachments:List<MessageAttachment> = emptyList(),currentImage:PromptImage? = null,materials:List<MessageMaterial> = emptyList(),currentMaterial:PromptMaterial? = null,
        stickers:List<MessageSticker> = emptyList(),currentSticker:PromptSticker? = null,includeVisuals:Boolean = true,
        memories:List<MemoryEntry> = emptyList(),environmentProfile:EnvironmentProfile?=null,now:Long=System.currentTimeMillis(),deviceZone:java.time.ZoneId=java.time.ZoneId.systemDefault(),
        plannerEntries:List<PlannerEntry> = emptyList(),cycleRecords:List<CycleRecord> = emptyList()):Resolved {
        if(role.scope()!=scope || chat.roleId!=role.id || history.any { it.chatId!=chat.id }) throw ChatFailure("ACCESS")
        require((text.isNotBlank()||currentImage!=null||currentMaterial!=null||currentSticker!=null) && text.length<=12000)
        require(listOfNotNull(currentImage,currentMaterial,currentSticker).size<=1)
        currentImage?.validate()
        currentMaterial?.validate()
        currentSticker?.validate()
        val historyById=history.associateBy {it.id}
        attachments.forEach {attachment->
            val owner=historyById[attachment.messageId]
            if(owner==null||owner.chatId!=attachment.chatId||owner.requestId!=attachment.requestId||owner.kind!="user")throw ChatFailure("ACCESS")
            attachment.promptImage().validate()
        }
        materials.forEach {material->
            val owner=historyById[material.messageId]
            if(owner==null||owner.chatId!=material.chatId||owner.requestId!=material.requestId||owner.kind!="user")throw ChatFailure("ACCESS")
            material.promptMaterial().validate()
        }
        stickers.forEach {sticker->
            val owner=historyById[sticker.messageId]
            if(owner==null||owner.chatId!=sticker.chatId||owner.requestId!=sticker.requestId||owner.kind!="user")throw ChatFailure("ACCESS")
            sticker.promptSticker().validate()
        }
        val payloadIds=listOf(attachments.map {it.messageId}.toSet(),materials.map {it.messageId}.toSet(),stickers.map {it.messageId}.toSet())
        if(payloadIds.indices.any {a->payloadIds.indices.any {b->a<b&&payloadIds[a].intersect(payloadIds[b]).isNotEmpty()}})throw ChatFailure("ACCESS")
        val complete=history.filter { it.kind=="assistant" && it.state==MessageState.COMPLETE }.map { it.requestId }.toSet()
        val raw=role.isRaw()
        val greeting=if(raw||chat.openingMessage.isEmpty()) emptyList() else listOf(PromptTurn("assistant",chat.openingMessage))
        val worldbook=Worldbook.select(scope,chat,history,text,entries,raw)
        val relations=Relationships.select(scope,chat,relationships,raw)
        val memoryQuery=(history.filter {it.state==MessageState.COMPLETE}.takeLast(6).map {it.content}+text).joinToString("\n").takeLast(Memories.RECALL_QUERY_LIMIT)
        val selectedMemories=Memories.select(scope,memories,raw,query=memoryQuery)
        if(environmentProfile!=null&&environmentProfile.scope()!=scope)throw ChatFailure("ACCESS")
        val environment=Environments.select(environmentProfile,raw,now,deviceZone)
        val environmentInstruction=environment.instruction.takeIf {it.isNotEmpty()}?.let {"\n\n环境信息（由用户明确开启，只描述请求冻结时的本机时间或已保存天气）：\n$it"}.orEmpty()
        val planner=PlannerRecords.select(scope,plannerEntries,cycleRecords,raw,now)
        val instructions=systemPrompt(role)+relations.instruction+selectedMemories.instruction+worldbook.instruction+environmentInstruction+planner.instruction
        val completeHistory=history.filter {it.requestId in complete}
        val stickerByMessage=stickers.associateBy {it.messageId}
        val attachmentByMessage=attachments.associateBy {it.messageId}
        val visualIds=if(includeVisuals)completeHistory.filter {it.kind=="user"&&(it.id in attachmentByMessage||it.id in stickerByMessage)}.map {it.id}.takeLast(StickerAssets.HISTORY_VISUALS).toSet() else emptySet()
        val eligible=attachments.filter {it.messageId in visualIds}.associateBy {it.messageId}
        val allAttachments=attachments.associateBy {it.messageId}
        val eligibleMaterials=materials.filter {it.requestId in complete}.takeLast(TextMaterials.HISTORY_MATERIALS).associateBy {it.messageId}
        val allMaterials=materials.associateBy {it.messageId}
        val turns=(if(instructions.isEmpty())emptyList()else listOf(PromptTurn("system",instructions))) + greeting +
            completeHistory.map {message->
                val image=eligible[message.id]?.promptImage()
                val material=eligibleMaterials[message.id]?.promptMaterial()
                val sticker=stickerByMessage[message.id]?.promptSticker()
                val stickerImage=sticker?.takeIf {message.id in visualIds}?.promptImage()
                val omitted=buildList {
                    if(allAttachments.containsKey(message.id)&&image==null)add("[较早图片未再次上传]")
                    allMaterials[message.id]?.takeIf {material==null}?.let {add("[较早资料未再次上传：${it.displayName}]")}
                }
                val content=listOf(message.content,sticker?.label().orEmpty(),omitted.joinToString("\n")).filter {it.isNotEmpty()}.joinToString("\n\n")
                PromptTurn(message.kind,content,listOfNotNull(image,stickerImage),material?.let {listOf(it)}?:emptyList())
            } + PromptTurn("user",listOf(text,currentSticker?.label().orEmpty()).filter {it.isNotEmpty()}.joinToString("\n\n"),
                listOfNotNull(currentImage,currentSticker?.takeIf {includeVisuals}?.promptImage()),currentMaterial?.let {listOf(it)}?:emptyList())
        if(turns.sumOf { it.modelText().length }>64000) throw ChatFailure("CONTEXT_LIMIT")
        if(turns.sumOf {turn->turn.images.sumOf {it.data.size}}>(MessageImages.HISTORY_IMAGES+1)*MessageImages.MAX_STORED)throw ChatFailure("CONTEXT_LIMIT")
        return Resolved(turns,worldbook,relations,selectedMemories,environment,planner)
    }
}
