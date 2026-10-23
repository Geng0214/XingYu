package com.ai.assistance.operit.data.backup

import com.ai.assistance.operit.data.model.HomeState
import com.ai.assistance.operit.data.model.HomeAction
import com.ai.assistance.operit.data.model.HomeActionCodec
import com.ai.assistance.operit.data.model.HomeScope
import com.ai.assistance.operit.data.model.RoleAccessScope

import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.core.tools.mcp.RemoteMcpSession
import com.ai.assistance.operit.core.tools.packTool.ToolPkgCodec
import com.ai.assistance.operit.data.db.ToolPkgAddress
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.StrictJson
import com.ai.assistance.operit.data.model.UserPersona
import com.ai.assistance.operit.data.model.Worldbook
import com.ai.assistance.operit.data.model.Relationships
import com.ai.assistance.operit.data.model.MessageImages
import com.ai.assistance.operit.data.model.PromptMaterial
import com.ai.assistance.operit.data.model.TextMaterials
import com.ai.assistance.operit.data.model.PromptSticker
import com.ai.assistance.operit.data.model.StickerAssets
import com.ai.assistance.operit.data.model.PromptVoice
import com.ai.assistance.operit.data.model.Reactions
import com.ai.assistance.operit.data.model.ScheduledMessage
import com.ai.assistance.operit.data.model.ScheduledMessages
import com.ai.assistance.operit.data.model.ImageGenerationConfig
import com.ai.assistance.operit.data.model.GeneratedImage
import com.ai.assistance.operit.data.model.ImageGenerations
import com.ai.assistance.operit.data.model.MessageState
import com.ai.assistance.operit.data.model.ReadingDocument
import com.ai.assistance.operit.data.model.ReadingDocuments
import com.ai.assistance.operit.data.model.Memories
import com.ai.assistance.operit.data.model.MemoryEntry
import com.ai.assistance.operit.data.model.DiaryEntry
import com.ai.assistance.operit.data.model.Diaries
import com.ai.assistance.operit.data.model.EnvironmentProfile
import com.ai.assistance.operit.data.model.Environments
import com.ai.assistance.operit.data.model.PlannerEntry
import com.ai.assistance.operit.data.model.CycleRecord
import com.ai.assistance.operit.data.model.PlannerRecords
import com.ai.assistance.operit.data.model.ProactiveRoleSetting
import com.ai.assistance.operit.data.model.ProactiveMessages
import com.ai.assistance.operit.api.image.ImageGenerationEndpoint
import com.ai.assistance.operit.api.audio.SpeechEndpoint
import com.ai.assistance.operit.data.model.SpeechServiceConfig
import com.ai.assistance.operit.data.model.SpeechServices
import org.json.JSONObject
import java.util.UUID

internal object BackupValidation {
    fun validate(s:BackupSnapshot)=try {
        require(s.tables.size==BackupSchema.tables.size&&s.createdAt>=0)
        require(s.tables.sumOf {it.size}<=BackupFormat.MAX_ROWS)
        s.tables.forEachIndexed {index,rows->
            val t=BackupSchema.tables[index]
            require(rows.all {it.table==t&&it.values.size==t.columns.size})
            require(rows.map {r->t.primary.map {r.text(it)}}.distinct().size==rows.size)
            rows.forEach {r->t.columns.forEach {c->
                val value=r[c.name]
                if(value==null)require(c.nullable)else when(c.type){
                    BackupType.TEXT->require(value is String&&value.toByteArray(Charsets.UTF_8).size<=c.limit&&'\u0000' !in value)
                    BackupType.INTEGER->require(value is Long)
                    BackupType.REAL->require(value is Double&&value.isFinite())
                    BackupType.BLOB->require(value is ByteArray&&value.size<=c.limit)
                }
                if(c.name in setOf("id","roleId","spaceId","chatId","configId","assistantId","messageId","extensionId","requestId","credentialRef","defaultConfigId")&&value!=null)
                    require(value is String&&value.isNotBlank()&&value.length<=128)
            }}
        }
        val roles=s.rows("roles").associateBy {it.text("id")}
        val models=s.rows("model_configs").associateBy {it.text("id")}
        val imageConfigs=s.rows("image_generation_configs").associateBy {it.text("id")}
        val chats=s.rows("chats").associateBy {it.text("id")}
        val messages=s.rows("messages").associateBy {it.text("id")}
        val memories=s.rows("memory_entries").associateBy {it.text("id")}
        val readingDocuments=s.rows("reading_documents").associateBy {it.text("id")}
        val extensions=s.rows("mcp_extensions").associateBy {it.text("id")}
        val tools=s.rows("extension_tools").associateBy {it.text("extensionId") to it.text("name")}
        val packages=s.rows("toolpkg_packages").associateBy {it.text("extensionId")}
        fun scope(r:BackupRow) {require(roles[r.text("roleId")]?.text("spaceId")==r.text("spaceId"))}
        fun positive(r:BackupRow,vararg fields:String){fields.forEach {require(r.number(it) in 1..Long.MAX_VALUE-1)}}
        fun bool(r:BackupRow,field:String){require(r.number(field) in 0..1)}
        fun definition(r:BackupRow) {val schema=r.text("inputSchema");require(schema.toByteArray().size<=65536&&r.text("description").length<=8000)
            StrictJson(schema).validate();require(JSONObject(schema).getString("type")=="object")}
        require(roles.values.map {it.text("spaceId")}.distinct().size==roles.size)
        roles.values.forEach {r->
            require(r.text("name").isNotBlank()&&r.text("name").length<=40&&r.text("prompt").isNotBlank()&&r.text("prompt").length<=8000&&r.text("openingMessage").length<=8000)
            require(r["defaultConfigId"]==null||models.containsKey(r["defaultConfigId"]))
            require(r.text("replyStyle") in setOf("short","paragraph"));positive(r,"version")
            bool(r,"personaEnabled");UserPersona.validate(r.text("userName"),r.text("userPersona"),r.number("personaEnabled")==1L)
            require(r.text("conversationMode") in setOf("character","raw"))
        }
        models.values.forEach {r->
            require(EndpointCompleter.completeEndpoint(r.text("endpoint"))==r.text("endpoint"))
            require(r.text("model").isNotBlank()&&r.text("model").length<=200&&r.number("timeoutSeconds") in 10..300);positive(r,"version")
            bool(r,"visionEnabled")
        }
        chats.values.forEach {r->require(roles.containsKey(r.text("roleId"))&&models.containsKey(r.text("configId"))&&r.number("createdAt")>=0&&r.text("openingMessage").length<=8000);bool(r,"pinned")}
        s.rows("worldbook_entries").forEach {r->
            scope(r);positive(r,"version");bool(r,"enabled")
            require(r["chatId"]==null||chats[r["chatId"]]?.text("roleId")==r.text("roleId"))
            require(r.number("priority") in 0..100)
            Worldbook.validate(r.text("title"),r.text("content"),r.text("trigger"),r.text("keywords"),r.number("priority").toInt())
        }
        s.rows("worldbook_entries").groupBy {it.text("roleId")}.values.forEach {require(it.size<=Worldbook.MAX_ENTRIES)}
        s.rows("relationship_entries").forEach {r->
            scope(r);positive(r,"version");bool(r,"enabled")
            require(r["chatId"]==null||chats[r["chatId"]]?.text("roleId")==r.text("roleId"))
            require(r.number("priority") in 0..100)
            Relationships.validate(r.text("sourceName"),r.text("targetName"),r.text("label"),r.text("description"),r.number("priority").toInt())
        }
        s.rows("relationship_entries").groupBy {it.text("roleId")}.values.forEach {require(it.size<=Relationships.MAX_ENTRIES)}
        s.rows("memory_entries").forEach {r->
            scope(r);bool(r,"enabled")
            val memory=MemoryEntry(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("kind"),r.text("title"),r.text("content"),r.number("enabled")==1L,
                r.number("priority").toInt(),r.text("source"),r.number("version"),r.number("createdAt"),r.number("updatedAt"),r["sourceChatId"] as String?,
                r["sourceRequestId"] as String?,r["sourceUserMessageId"] as String?,r["sourceAssistantMessageId"] as String?,r["sourceModel"] as String?,
                r["sourceConfigVersion"] as Long?,r["sourceMessageAt"] as Long?,r["extractedAt"] as Long?,r.text("recallMode"),r["expiresAt"] as Long?,
                r["forgottenAt"] as Long?,r["replacedById"] as String?)
            Memories.validateStored(memory)
            memory.replacedById?.let {replacement->require(memories[replacement]?.let {it.text("roleId")==memory.roleId&&it.text("spaceId")==memory.spaceId}==true)}
            if(memory.sourceLinked()) {
                val chat=chats[requireNotNull(memory.sourceChatId)]
                val user=messages[requireNotNull(memory.sourceUserMessageId)]
                val assistant=messages[requireNotNull(memory.sourceAssistantMessageId)]
                require(chat?.text("roleId")==memory.roleId&&user?.text("chatId")==memory.sourceChatId&&assistant?.text("chatId")==memory.sourceChatId)
                require(user.text("requestId")==memory.sourceRequestId&&assistant.text("requestId")==memory.sourceRequestId&&user.text("kind")=="user"&&assistant.text("kind")=="assistant")
                require(user.text("state")=="COMPLETE"&&assistant.text("state")=="COMPLETE"&&assistant.number("createdAt")==memory.sourceMessageAt)
            }
        }
        s.rows("memory_entries").groupBy {it.text("roleId")}.values.forEach {require(it.size<=Memories.MAX_ENTRIES)}
        s.rows("diary_entries").forEach {r->
            scope(r)
            val entry=DiaryEntry(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("notebook"),r.text("title"),r.text("content"),r.text("mood"),r.text("source"),
                r["sourceModel"] as String?,r["sourceConfigVersion"] as Long?,r["generationPrompt"] as String?,r.number("occurredAt"),r.number("version"),r.number("createdAt"),r.number("updatedAt"))
            Diaries.validateStored(entry)
        }
        s.rows("diary_entries").groupBy {it.text("roleId")}.values.forEach {require(it.size<=Diaries.MAX_ENTRIES)}
        s.rows("environment_profiles").forEach {r->
            scope(r);bool(r,"timeContextEnabled");bool(r,"weatherContextEnabled")
            Environments.validateStored(EnvironmentProfile(r.text("roleId"),r.text("spaceId"),r.number("timeContextEnabled")==1L,r.number("weatherContextEnabled")==1L,r.text("provider"),
                r["locationId"] as Long?,r["locationName"] as String?,r["admin1"] as String?,r["country"] as String?,r["countryCode"] as String?,
                r["latitude"] as Double?,r["longitude"] as Double?,r["timezone"] as String?,r["weatherTime"] as String?,r["temperature"] as Double?,
                r["apparentTemperature"] as Double?,r["humidity"] as Long?,r["precipitation"] as Double?,r["weatherCode"] as Long?,r["windSpeed"] as Double?,
                r["todayMin"] as Double?,r["todayMax"] as Double?,r["todayPrecipitationProbability"] as Long?,r["fetchedAt"] as Long?,r.number("version"),r.number("updatedAt")))
        }
        s.rows("planner_entries").forEach {r->scope(r);bool(r,"shareWithRole");PlannerRecords.validatePlanner(PlannerEntry(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("kind"),r.text("title"),r.text("note"),r.number("startsAt"),r["endsAt"] as Long?,r.text("zoneId"),r["completedAt"] as Long?,r["reminderMinutesBefore"] as Long?,r.number("shareWithRole")==1L,r.number("version"),r.number("createdAt"),r.number("updatedAt")))}
        s.rows("cycle_records").forEach {r->scope(r);bool(r,"shareWithRole");PlannerRecords.validateCycle(CycleRecord(r.text("id"),r.text("roleId"),r.text("spaceId"),r.number("startedOn"),r["endedOn"] as Long?,r.text("flowLevel"),r.text("note"),r.number("shareWithRole")==1L,r.number("version"),r.number("createdAt"),r.number("updatedAt")))}
        s.rows("planner_entries").groupBy {it.text("roleId")}.values.forEach {require(it.size<=PlannerRecords.MAX_PLANNER)}
        s.rows("cycle_records").groupBy {it.text("roleId")}.values.forEach {require(it.size<=PlannerRecords.MAX_CYCLES)}
        s.rows("proactive_role_settings").forEach {r->
            scope(r);bool(r,"enabled")
            val role=requireNotNull(roles[r.text("roleId")]);val chat=requireNotNull(chats[r.text("chatId")]);val config=requireNotNull(models[r.text("configId")])
            require(role.text("conversationMode")=="character"&&chat.text("roleId")==r.text("roleId"))
            ProactiveMessages.validate(ProactiveRoleSetting(r.text("roleId"),r.text("spaceId"),r.number("enabled")==1L,r.text("chatId"),r.text("configId"),
                r.number("quietStartMinute").toInt(),r.number("quietEndMinute").toInt(),r.number("minIntervalHours").toInt(),r.number("dailyLimit").toInt(),
                r.number("nextEligibleAt"),r.number("version"),r.number("updatedAt")))
        }
        s.rows("proactive_requests").forEach {r->
            scope(r);bool(r,"notificationSent")
            val chat=requireNotNull(chats[r.text("chatId")]);val message=requireNotNull(messages[r.text("assistantId")]);val config=requireNotNull(models[r.text("configId")])
            require(chat.text("roleId")==r.text("roleId")&&message.text("chatId")==r.text("chatId")&&message.text("requestId")==r.text("requestId")&&message.text("kind")=="assistant")
            require(r.number("configVersion")>0&&r.number("dueAt")>=0&&r.number("createdAt")>=0)
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(r.text("dayKey")))
            require(r.text("state") in setOf("PENDING","COMPLETE","FAILED","CANCELLED"))
            if(r.text("state")=="PENDING")require(message.text("state") in setOf("QUEUED","WAITING","GENERATING")&&r.number("finishedAt")==0L)
            else require(message.text("state")==r.text("state")&&message.text("errorCode")==r.text("errorCode")&&r.number("finishedAt")>=r.number("createdAt"))
        }
        require(s.rows("proactive_requests").map {it.text("roleId") to it.number("dueAt")}.distinct().size==s.rows("proactive_requests").size)
        messages.values.forEach {r->
            require(chats.containsKey(r.text("chatId"))&&r.text("kind") in setOf("user","assistant"))
            require(r.text("state") in setOf("QUEUED","WAITING","GENERATING","COMPLETE","CANCELLED","FAILED"))
            require(r.text("bubbleStyle") in setOf("short","paragraph")&&r.number("createdAt")>=0);bool(r,"favorite")
        }
        // A request must never join two role spaces, even when the archive is correctly encrypted.
        messages.values.groupBy {it.text("requestId")}.values.forEach {group->require(group.map {it.text("chatId")}.distinct().size==1&&group.map {it.text("kind")}.distinct().size==group.size)}
        s.rows("message_attachments").forEach {r->
            val message=messages[r.text("messageId")]
            require(message?.text("kind")=="user"&&message.text("chatId")==r.text("chatId")&&message.text("requestId")==r.text("requestId"))
            require(r.text("mimeType")==MessageImages.MIME_TYPE&&r.number("width") in 1..MessageImages.MAX_EDGE.toLong()&&r.number("height") in 1..MessageImages.MAX_EDGE.toLong())
            val data=r["data"] as ByteArray
            require(data.isNotEmpty()&&data.size<=MessageImages.MAX_STORED&&r.text("sha256")==MessageImages.sha256(data))
        }
        s.rows("message_materials").forEach {r->
            val message=messages[r.text("messageId")]
            require(message?.text("kind")=="user"&&message.text("chatId")==r.text("chatId")&&message.text("requestId")==r.text("requestId"))
            require(r.number("originalChars") in 0..TextMaterials.MAX_ORIGINAL_CHARS.toLong());bool(r,"truncated")
            val material=PromptMaterial(r.text("kind"),r.text("displayName"),r["sourceUrl"] as String?,r.text("mimeType"),r.text("text"),r.number("originalChars").toInt(),r.number("truncated")==1L,r.text("sha256")).also(PromptMaterial::validate)
            if(material.kind==TextMaterials.KIND_READING) {
                val reference=ReadingDocuments.parseReference(requireNotNull(material.sourceUrl))
                val document=readingDocuments[reference.documentId]?:error("Missing reading document")
                val chat=requireNotNull(chats[message.text("chatId")])
                require(document.text("roleId")==chat.text("roleId")&&document.text("spaceId")==roles[chat.text("roleId")]?.text("spaceId"))
                val source=document.text("text");require(reference.end<=source.length&&source.substring(reference.start,reference.end)==material.text)
                val stored=ReadingDocument(document.text("id"),document.text("roleId"),document.text("spaceId"),document.text("displayName"),document.text("mimeType"),source,document.text("chaptersJson"),document.number("currentChapter").toInt(),document.number("currentOffset").toInt(),document.text("sha256"),document.number("version"),document.number("createdAt"),document.number("updatedAt"))
                val chapter=stored.chapters().single {reference.start>=it.start&&reference.end<=it.end}
                val expected=ReadingDocuments.passage(stored,chapter.index,reference.start,reference.end)
                require(expected.displayName==material.displayName&&expected.mimeType==material.mimeType&&expected.sha256==material.sha256)
            }
        }
        s.rows("sticker_assets").forEach {r->
            scope(r);bool(r,"favorite");require(r.number("createdAt")>=0)
            PromptSticker(null,r.text("displayName"),r.text("mimeType"),r.number("width").toInt(),r.number("height").toInt(),r.text("sha256"),r["data"] as ByteArray).validate()
        }
        s.rows("sticker_assets").groupBy {it.text("roleId")}.values.forEach {require(it.size<=StickerAssets.MAX_PER_ROLE)}
        s.rows("message_stickers").forEach {r->
            val message=messages[r.text("messageId")]
            require(message?.text("kind")=="user"&&message.text("chatId")==r.text("chatId")&&message.text("requestId")==r.text("requestId"))
            PromptSticker(null,r.text("displayName"),r.text("mimeType"),r.number("width").toInt(),r.number("height").toInt(),r.text("sha256"),r["data"] as ByteArray).validate()
        }
        s.rows("message_voices").forEach {r->
            val message=messages[r.text("messageId")]
            require(message?.text("kind")=="user"&&message.text("chatId")==r.text("chatId")&&message.text("requestId")==r.text("requestId"))
            PromptVoice(r.number("sampleRate").toInt(),r.number("durationMs"),r["waveform"] as ByteArray,r.text("sha256"),r["data"] as ByteArray).validate()
        }
        s.rows("message_reactions").forEach {r->
            val message=messages[r.text("messageId")]
            require(message!=null&&message.text("chatId")==r.text("chatId"))
            require(Reactions.validSide(r.text("side"))&&r.text("emoji") in StickerAssets.EMOJI&&r.number("createdAt")>=0)
        }
        s.rows("message_reactions").groupBy {it.text("messageId")+"|"+it.text("side")}.values.forEach {require(it.size<=Reactions.MAX_PER_SIDE)}
        s.rows("scheduled_messages").forEach {r->
            scope(r)
            require(chats[r.text("chatId")]?.text("roleId")==r.text("roleId"))
            ScheduledMessages.validate(ScheduledMessage(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("chatId"),r.text("configId"),r.number("configVersion"),r.number("dueAt"),r.text("mode"),r.text("content"),r.text("state"),r.text("assistantId"),r.text("errorCode"),r.number("notificationSent")!=0L,r.number("createdAt"),r.number("finishedAt")))
        }
        val payloadMessageIds=listOf("message_attachments","message_materials","message_stickers","message_voices").map {table->s.rows(table).map {it.text("messageId")}.toSet()}
        require(payloadMessageIds.indices.none {a->payloadMessageIds.indices.any {b->a<b&&payloadMessageIds[a].intersect(payloadMessageIds[b]).isNotEmpty()}})
        imageConfigs.values.forEach {r->
            val config=ImageGenerationConfig(r.text("id"),r.text("name"),r.text("endpoint"),r.text("model"),r.number("timeoutSeconds").toInt(),r.text("credentialRef"),r.number("version"),r.text("size"))
            ImageGenerations.validateConfig(config);require(ImageGenerationEndpoint.complete(config.endpoint)==config.endpoint)
        }
        s.rows("speech_service_configs").forEach {r->
            val config=SpeechServiceConfig(r.text("id"),r.text("name"),r.text("kind"),r.text("endpoint"),r.text("model"),r.text("voice"),r.number("timeoutSeconds").toInt(),r.text("credentialRef"),r.number("version"))
            SpeechServices.validateConfig(config);require(SpeechEndpoint.complete(config.endpoint,config.kind)==config.endpoint)
        }
        s.rows("generated_images").forEach {r->
            scope(r);val chat=chats[r.text("chatId")]
            require(chat?.text("roleId")==r.text("roleId")&&imageConfigs.containsKey(r.text("configId")))
            val image=GeneratedImage(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("chatId"),r.text("configId"),r.number("configVersion"),r.text("model"),r.text("prompt"),r.text("requestedSize"),MessageState.valueOf(r.text("state")),r.text("errorCode"),r.text("mimeType"),r.number("width").toInt(),r.number("height").toInt(),r.text("sha256"),r["data"] as ByteArray?,r.number("createdAt"),r.number("finishedAt"),r.number("elapsedMs"))
            ImageGenerations.validateStored(image)
        }
        readingDocuments.values.forEach {r->
            scope(r)
            ReadingDocuments.validateStored(ReadingDocument(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("displayName"),r.text("mimeType"),r.text("text"),r.text("chaptersJson"),r.number("currentChapter").toInt(),r.number("currentOffset").toInt(),r.text("sha256"),r.number("version"),r.number("createdAt"),r.number("updatedAt")))
        }
        readingDocuments.values.groupBy {it.text("roleId")}.values.forEach {require(it.size<=ReadingDocuments.MAX_DOCUMENTS_PER_ROLE)}
        s.rows("role_appearance").forEach {r->scope(r);require(r["dimming"] as Double in 0.0..0.650001&&r.text("palette") in setOf("rose","green","blue"))}
        s.rows("role_portraits").forEach {r->scope(r);require(r.text("kind") in setOf("self","role")&&(r["image"] as ByteArray).isNotEmpty())}
        extensions.values.forEach {r->
            bool(r,"enabled");positive(r,"version");require(r.text("name").isNotBlank()&&r.text("name").length<=80&&r.number("timeoutMs") in 250..300000)
            if(ToolPkgAddress.isLocal(r.text("endpoint")))require(packages.containsKey(r.text("id"))&&r["credentialRef"]==null)
            else require(RemoteMcpSession.normalizeEndpoint(r.text("endpoint"))==r.text("endpoint")&&!packages.containsKey(r.text("id")))
        }
        tools.values.forEach {r->require(extensions.containsKey(r.text("extensionId"))&&r.text("name").isNotBlank()&&r.text("name").length<=128);definition(r)}
        tools.values.groupBy {it.text("extensionId")}.values.forEach {require(it.size<=128)}
        packages.values.forEach {r->
            val archive=r["archive"] as ByteArray;val bundle=ToolPkgCodec.decode(archive)
            require(bundle.sha256==r.text("sha256")&&bundle.packageId==r.text("packageId")&&bundle.packageVersion==r.text("packageVersion")&&bundle.apiVersion==r.text("apiVersion"))
            require(extensions[r.text("extensionId")]?.text("endpoint")==ToolPkgAddress.fromHash(bundle.sha256))
            val actual=s.rows("extension_tools").filter {it.text("extensionId")==r.text("extensionId")}
            require(actual.size==bundle.tools.size&&bundle.tools.all {tool->actual.any {it.text("name")==tool.name&&it.text("description")==tool.definition.description&&it.text("inputSchema")==tool.definition.inputSchema}})
        }
        s.rows("role_tool_grants").forEach {r->scope(r);require(tools.containsKey(r.text("extensionId") to r.text("toolName")));positive(r,"version");bool(r,"enabled")}
        fun owner(r:BackupRow) {
            scope(r);val chat=chats[r.text("chatId")];val message=messages[r.text("assistantId")]
            require(chat?.text("roleId")==r.text("roleId")&&message?.text("chatId")==r.text("chatId")&&message.text("requestId")==r.text("requestId")&&message.text("kind")=="assistant")
        }
        s.rows("request_tool_permissions").forEach {r->owner(r);positive(r,"configVersion","grantVersion");definition(r)
            if(ToolPkgAddress.isLocal(r.text("endpoint"))){ToolPkgAddress.hash(r.text("endpoint"));require(r["credentialRef"]==null)}else require(RemoteMcpSession.normalizeEndpoint(r.text("endpoint"))==r.text("endpoint"))
        }
        s.rows("tool_invocations").forEach {r->owner(r);require(r.text("state") in setOf("WAITING_APPROVAL","RUNNING","COMPLETE","FAILED","CANCELLED"))}
        val bindings=bindings(s)
        val musicTracks=s.rows("music_tracks").associate {r->
            scope(r)
            val track=com.ai.assistance.operit.data.model.MusicTrack(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("displayName"),r.text("mimeType"),r.number("durationMs"),r.number("byteCount"),r.text("sha256"),r["sourceUri"] as String?,r.number("version"),r.number("createdAt"),r.text("mediaKind"),(r["videoWidth"] as Long?)?.let {require(it in 1..16384);it.toInt()},(r["videoHeight"] as Long?)?.let {require(it in 1..16384);it.toInt()})
            com.ai.assistance.operit.data.model.LocalMusic.validate(track);track.id to track
        }
        require(musicTracks.values.groupingBy {it.roleId}.eachCount().values.all {it<=com.ai.assistance.operit.data.model.LocalMusic.MAX_TRACKS})
        s.rows("music_sessions").forEach {r->
            scope(r);require(chats[r.text("chatId")]?.text("roleId")==r.text("roleId"))
            com.ai.assistance.operit.data.model.LocalMusic.validate(com.ai.assistance.operit.data.model.MusicSession(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("chatId"),r.text("trackId"),r.text("state"),r.number("positionMs"),r.text("note"),r["playbackToken"] as String?,r.number("version"),r.number("createdAt"),r.number("updatedAt")),requireNotNull(musicTracks[r.text("trackId")]))
        }
        s.rows("bilibili_watches").forEach {r->
            scope(r);require(chats[r.text("chatId")]?.text("roleId")==r.text("roleId"));require(r.number("part") in 1..1000)
            com.ai.assistance.operit.data.model.BilibiliWatching.validate(com.ai.assistance.operit.data.model.BilibiliWatch(
                r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("chatId"),r.text("title"),r.text("videoId"),r.number("part").toInt(),r.number("positionMs"),r["durationMs"] as Long?,r.text("note"),r["playbackToken"] as String?,r.number("version"),r.number("createdAt"),r.number("updatedAt")))
        }
        s.rows("shared_activities").forEach {r->
            scope(r)
            require(chats[r.text("chatId")]?.text("roleId")==r.text("roleId"))
            val d=requireNotNull(readingDocuments[r.text("readingDocumentId")])
            require(r.number("chapter") in 0..Int.MAX_VALUE.toLong()&&r.number("offset") in 0..Int.MAX_VALUE.toLong())
            val document=ReadingDocument(d.text("id"),d.text("roleId"),d.text("spaceId"),d.text("displayName"),d.text("mimeType"),d.text("text"),d.text("chaptersJson"),d.number("currentChapter").toInt(),d.number("currentOffset").toInt(),d.text("sha256"),d.number("version"),d.number("createdAt"),d.number("updatedAt"))
            com.ai.assistance.operit.data.model.SharedActivities.validate(com.ai.assistance.operit.data.model.SharedActivity(
                r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("chatId"),r.text("readingDocumentId"),r.text("kind"),r.text("state"),r.number("chapter").toInt(),r.number("offset").toInt(),r.text("note"),r.number("version"),r.number("createdAt"),r.number("updatedAt")),document)
        }
        GroupBackup.validate(s)
        PlayBackup.validate(s)
        HomeBackup.validate(s)
        require(s.credentials.map {it.ref}.distinct().size==s.credentials.size)
        require(if(s.includesCredentials)s.credentials.map {it.ref}.toSet()==bindings.keys else s.credentials.isEmpty())
        s.credentials.forEach {require(bindings[it.ref]==it.endpoint&&it.key.isNotBlank()&&it.key.length<=8192&&it.key.none(Char::isISOControl))}
    }catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("BACKUP_FORMAT")}

    fun bindings(s:BackupSnapshot):Map<String,String> {
        val bindings=linkedMapOf<String,String>()
        listOf("model_configs","image_generation_configs","speech_service_configs","mcp_extensions","request_tool_permissions").forEach {table->s.rows(table).forEach {r->
            val ref=r["credentialRef"] as String?
            if(ref!=null){val endpoint=r.text("endpoint");require(!bindings.containsKey(ref)||bindings[ref]==endpoint);bindings[ref]=endpoint}
        }}
        return bindings
    }
}

/** Normalize older archives before their IDs are rebound to the one-thread-per-contact schema. */
internal object SingleConversationArchive {
    fun merge(snapshot: BackupSnapshot): BackupSnapshot {
        val messagesAt = snapshot.rows("messages").groupBy { it.text("chatId") }
            .mapValues { (_, rows) -> rows.maxOf { it.number("createdAt") } }
        val imagesAt = snapshot.rows("generated_images").groupBy { it.text("chatId") }
            .mapValues { (_, rows) -> rows.maxOf { it.number("createdAt") } }
        val aliases = mutableMapOf<String, String>()
        val kept = mutableMapOf<String, BackupRow>()
        val extraOpenings = mutableListOf<Pair<BackupRow, String>>()
        snapshot.rows("chats").groupBy { it.text("roleId") }.values.forEach { chats ->
            fun activity(row: BackupRow) = maxOf(
                row.number("createdAt"), messagesAt[row.text("id")] ?: 0L,
                imagesAt[row.text("id")] ?: 0L
            )
            val current = chats.maxWith(compareBy<BackupRow> { activity(it) }
                .thenBy { it.number("createdAt") }.thenBy { it.text("id") })
            val currentId = current.text("id")
            kept[currentId] = current.changing(mapOf("pinned" to if (chats.any { it.number("pinned") == 1L }) 1L else 0L))
            chats.forEach { chat ->
                aliases[chat.text("id")] = currentId
                if (chat !== current && chat.text("openingMessage").isNotEmpty()) {
                    extraOpenings += chat to currentId
                }
            }
        }
        val messageTable = BackupSchema.tables.first { it.name == "messages" }
        val openingMessages = extraOpenings.map { (old, currentId) ->
            val values = mapOf<String, Any?>(
                "id" to UUID.randomUUID().toString(), "chatId" to currentId,
                "requestId" to UUID.randomUUID().toString(), "kind" to "assistant",
                "content" to old.text("openingMessage"), "state" to "COMPLETE",
                "createdAt" to old.number("createdAt"), "errorCode" to "",
                "firstTokenMs" to -1L, "elapsedMs" to -1L,
                "bubbleStyle" to "paragraph", "favorite" to 0L,
                "quotedMessageId" to null, "quotedSpeaker" to "", "quotedPreview" to ""
            )
            BackupRow(messageTable, messageTable.columns.map { column -> values[column.name] })
        }
        val tables = snapshot.tables.mapIndexed { index, rows ->
            val table = BackupSchema.tables[index]
            if (table.name == "chats") kept.values.toList()
            else {
                val mapped = rows.map { row ->
                    val changes = mutableMapOf<String, Any?>()
                    listOf("chatId", "sourceChatId").forEach { column ->
                        if (table.columns.any { it.name == column }) {
                            val oldId = row[column] as String?
                            if (oldId != null) changes[column] = requireNotNull(aliases[oldId])
                        }
                    }
                    row.changing(changes)
                }
                if (table.name == "messages") mapped + openingMessages else mapped
            }
        }
        return snapshot.copy(tables = tables)
    }
}

/** Append-only restore: historical IDs cannot overwrite local roles, credentials or permissions. */
internal object BackupRemap {
    fun create(s:BackupSnapshot,credentialRefs:Map<String,String>,now:Long):BackupSnapshot {
        val normalized=SingleConversationArchive.merge(s)
        val maps=mutableMapOf<String,MutableMap<String,String>>()
        fun identity(domain:String,value:String)=maps.getOrPut(domain){mutableMapOf()}.getOrPut(value){UUID.randomUUID().toString()}
        val rows=normalized.tables.map {table->table.map {r->
            val changes=mutableMapOf<String,Any?>()
            r.table.columns.forEach {c->val value=r[c.name]
                val domain=when(c.name){
                    "id"->when(r.table.name){"home_world"->null;"play_sessions"->"play_activity";"group_members"->"group_member";"group_messages"->"group_message";"roles"->"role";"chats"->"chat";"messages"->"message";"model_configs"->"config";"image_generation_configs"->"image_config";"generated_images"->"generated_image";"music_tracks"->"music_track";"bilibili_watches"->"bilibili_watch";"music_sessions"->"music_session";"shared_activities"->"shared_activity";"speech_service_configs"->"speech_config";"reading_documents"->"reading_document";"mcp_extensions"->"extension";"worldbook_entries"->"worldbook";"relationship_entries"->"relationship";"memory_entries"->"memory";"diary_entries"->"diary";"planner_entries"->"planner";"cycle_records"->"cycle";"sticker_assets"->"sticker";"scheduled_messages"->"scheduled_message";else->error("Unknown ID")}
                    "commandId"->"home_command";"activityId"->"play_activity";"trackId"->"music_track";"readingDocumentId"->"reading_document";"roleId"->"role";"spaceId"->if(r.table.name.startsWith("group_"))"group_space"else "space";"groupId"->"group";"roleSpaceId"->"space";"participantId"->"group_member";"userId"->"group_message";"chatId","sourceChatId"->"chat";"configId"->if(r.table.name=="generated_images")"image_config" else "config";"defaultConfigId"->"config"
                    "assistantId","messageId","sourceUserMessageId","sourceAssistantMessageId"->when {r.table.name.startsWith("play_")->"play_assistant";r.table.name.startsWith("group_")->"group_message";else->"message"};"requestId","sourceRequestId"->when {r.table.name.startsWith("play_")->"play_request";r.table.name.startsWith("group_")->"group_request";else->"request"};"replacedById"->"memory";"extensionId"->"extension";else->null
                }
                if(domain!=null&&value!=null)changes[c.name]=identity(domain,value as String)
                if(c.name=="credentialRef"&&value!=null)changes[c.name]=requireNotNull(credentialRefs[value])
            }
            if(r.table.name=="home_world") {
                listOf("initialJson","stateJson").forEach {column->changes[column]=com.ai.assistance.operit.data.model.HomeCodec.remap(r.text(column),{identity("role",it)},{identity("space",it)})}
            }
            if(r.table.name=="home_events") {
                val action=HomeActionCodec.decode(r.text("actionJson"))
                changes["actionJson"]=HomeActionCodec.encode(HomeActionCodec.remap(action,{identity("role",it)},{identity("space",it)},{identity("play_activity",it)}))
            }
            if(r.table.name=="play_sessions"&&r["requestId"]!=null) {
                require(r.number("version")<Long.MAX_VALUE)
                changes["requestId"]=null;changes["status"]="INTERRUPTED";changes["version"]=r.number("version")+1
                changes["updatedAt"]=maxOf(now,r.number("updatedAt"))
            }
            if(r.table.name=="play_requests"&&r.text("state") in setOf("QUEUED","WAITING","GENERATING")){changes["state"]="FAILED";changes["errorCode"]="INTERRUPTED"}
            if(r.table.name=="group_requests"&&r.text("terminalState")=="PENDING"){changes["terminalState"]="FAILED";changes["errorCode"]="INTERRUPTED"}
            if(r.table.name=="group_messages"&&r.text("state") in setOf("QUEUED","WAITING","GENERATING")){changes["state"]="FAILED";changes["errorCode"]="INTERRUPTED"}
            if(r.table.name=="bilibili_watches")changes["playbackToken"]=null
            if(r.table.name=="music_tracks")changes["sourceUri"]=null
            if(r.table.name=="music_sessions"){changes["playbackToken"]=null;if(r.text("state")=="playing")changes["state"]="paused"}
            if(r.table.name=="message_materials"&&r.text("kind")==TextMaterials.KIND_READING) {
                val reference=ReadingDocuments.parseReference(r.text("sourceUrl"))
                changes["sourceUrl"]=ReadingDocuments.reference(identity("reading_document",reference.documentId),reference.start,reference.end)
            }
            if(r.table.name in setOf("mcp_extensions","role_tool_grants"))changes["enabled"]=0L
            if(r.table.name=="messages"&&r.text("state") in setOf("QUEUED","WAITING","GENERATING")){changes["state"]="FAILED";changes["errorCode"]="INTERRUPTED"}
            if(r.table.name=="tool_invocations"&&r.text("state") in setOf("WAITING_APPROVAL","RUNNING")){changes["state"]="FAILED";changes["errorCode"]="INTERRUPTED";changes["finishedAt"]=now}
            if(r.table.name=="proactive_role_settings"){changes["enabled"]=0L;changes["nextEligibleAt"]=now;changes["version"]=r.number("version")+1;changes["updatedAt"]=now}
            if(r.table.name=="scheduled_messages"&&r.text("state")==ScheduledMessages.STATE_PENDING){changes["state"]=ScheduledMessages.STATE_CANCELLED;changes["finishedAt"]=now}
            if(r.table.name=="proactive_requests"&&r.text("state")=="PENDING"){changes["state"]="FAILED";changes["errorCode"]="INTERRUPTED";changes["notificationSent"]=0L;changes["finishedAt"]=now}
            if(r.table.name=="generated_images"&&r.text("state") in setOf("QUEUED","WAITING","GENERATING")){changes["state"]="FAILED";changes["errorCode"]="INTERRUPTED";changes["finishedAt"]=now;changes["elapsedMs"]=(now-r.number("createdAt")).coerceAtLeast(0)}
            r.changing(changes)
        }}
        return BackupSnapshot(s.createdAt,rows,false,emptyList())
    }
}
