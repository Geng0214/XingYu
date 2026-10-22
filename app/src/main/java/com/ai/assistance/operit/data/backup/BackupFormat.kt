package com.ai.assistance.operit.data.backup

import com.ai.assistance.operit.data.model.ChatFailure
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal enum class BackupType { TEXT, INTEGER, REAL, BLOB }
internal data class BackupColumn(val name:String,val type:BackupType,val nullable:Boolean=false,val limit:Int=65536)
internal data class BackupTable(val name:String,val columns:List<BackupColumn>,val primary:List<String>)
internal data class BackupRow(val table:BackupTable,val values:List<Any?>) {
    operator fun get(name:String):Any?=values[table.columns.indexOfFirst {it.name==name}.also {require(it>=0)}]
    fun text(name:String)=get(name) as String
    fun number(name:String)=get(name) as Long
    fun changing(changes:Map<String,Any?>)=copy(values=table.columns.mapIndexed {i,c->if(changes.containsKey(c.name))changes[c.name]else values[i]})
}
internal data class BackupCredential(val ref:String,val endpoint:String,val key:String)
internal data class BackupSnapshot(val createdAt:Long,val tables:List<List<BackupRow>>,val includesCredentials:Boolean,val credentials:List<BackupCredential>) {
    fun rows(name:String)=tables[BackupSchema.tables.indexOfFirst {it.name==name}.also {require(it>=0)}]
    fun summary()=BackupSummary(createdAt,rows("roles").size,rows("chats").size,rows("messages").size,rows("model_configs").size,
        rows("role_portraits").size+rows("role_appearance").count {it["wallpaper"]!=null}+rows("message_attachments").size+rows("sticker_assets").size+rows("message_stickers").size+rows("generated_images").count {it["data"]!=null},rows("mcp_extensions").size,credentials.size,includesCredentials,
        rows("roles").count {it.text("userName").isNotEmpty()||it.text("userPersona").isNotEmpty()},rows("worldbook_entries").size,rows("relationship_entries").size,rows("message_materials").size,rows("sticker_assets").size,rows("message_stickers").size,rows("image_generation_configs").size,rows("generated_images").size,rows("reading_documents").size,rows("memory_entries").size,rows("diary_entries").size,rows("environment_profiles").size,rows("planner_entries").size,rows("cycle_records").size,rows("proactive_role_settings").size,rows("proactive_requests").size,rows("speech_service_configs").size,rows("shared_activities").size,rows("music_tracks").size,rows("music_sessions").size,rows("bilibili_watches").size,rows("group_rooms").size,rows("group_messages").size,rows("play_sessions").size,rows("play_requests").size,rows("home_world").size,rows("home_events").size)
}
data class BackupSummary(val createdAt:Long,val roles:Int,val chats:Int,val messages:Int,val models:Int,val images:Int,val extensions:Int,val credentials:Int,val includesCredentials:Boolean,val personas:Int=0,val worldbookEntries:Int=0,val relationships:Int=0,val materials:Int=0,val stickerAssets:Int=0,val stickerMessages:Int=0,val imageGenerationConfigs:Int=0,val generatedImages:Int=0,val readingDocuments:Int=0,val memories:Int=0,val diaries:Int=0,val environments:Int=0,val plannerEntries:Int=0,val cycleRecords:Int=0,val proactiveSettings:Int=0,val proactiveRequests:Int=0,val speechServiceConfigs:Int=0,val sharedActivities:Int=0,val musicTracks:Int=0,val musicSessions:Int=0,val bilibiliWatches:Int=0,val groups:Int=0,val groupMessages:Int=0,val playSessions:Int=0,val playRequests:Int=0,val homes:Int=0,val homeCommands:Int=0)

/** Fixed logical schema: archive content can never select SQL tables, columns or file paths. */
internal object BackupSchema {
    private fun t(name:String,n:Boolean=false,max:Int=65536)=BackupColumn(name,BackupType.TEXT,n,max)
    private fun i(name:String)=BackupColumn(name,BackupType.INTEGER)
    private fun b(name:String,n:Boolean=false,max:Int)=BackupColumn(name,BackupType.BLOB,n,max)
    private fun table(name:String,pk:String,vararg c:BackupColumn)=BackupTable(name,c.toList(),pk.split(','))
    val tablesV1=listOf(
        table("model_configs","id",t("id"),t("name"),t("provider"),t("endpoint"),t("model"),i("timeoutSeconds"),t("credentialRef"),i("version")),
        table("roles","id",t("id"),t("name"),t("prompt"),i("version"),t("spaceId"),t("openingMessage"),t("defaultConfigId",true),t("replyStyle")),
        table("chats","id",t("id"),t("roleId"),t("configId"),t("title"),i("createdAt"),t("openingMessage"),i("pinned")),
        table("messages","id",t("id"),t("chatId"),t("requestId"),t("kind"),t("content",max=2*1024*1024),t("state"),i("createdAt"),t("errorCode"),i("firstTokenMs"),i("elapsedMs"),t("bubbleStyle"),i("favorite")),
        table("role_appearance","roleId",t("roleId"),t("spaceId"),b("wallpaper",true,1536*1024),BackupColumn("dimming",BackupType.REAL),t("palette")),
        table("role_portraits","roleId,kind",t("roleId"),t("spaceId"),t("kind"),b("image",max=65536)),
        table("mcp_extensions","id",t("id"),t("name"),t("endpoint"),t("credentialRef",true),i("timeoutMs"),i("enabled"),i("version")),
        table("extension_tools","extensionId,name",t("extensionId"),t("name"),t("description",max=262144),t("inputSchema",max=262144)),
        table("toolpkg_packages","extensionId",t("extensionId"),t("packageId"),t("packageVersion"),t("apiVersion"),t("sha256"),b("archive",max=1024*1024),i("installedAt")),
        table("role_tool_grants","roleId,spaceId,extensionId,toolName",t("roleId"),t("spaceId"),t("extensionId"),t("toolName"),i("enabled"),i("version")),
        table("request_tool_permissions","requestId,extensionId,toolName",t("requestId"),t("extensionId"),t("toolName"),t("roleId"),t("spaceId"),t("chatId"),t("assistantId"),i("configVersion"),i("grantVersion"),t("endpoint"),t("credentialRef",true),i("timeoutMs"),t("description",max=262144),t("inputSchema",max=262144)),
        table("tool_invocations","requestId,callId",t("requestId"),t("callId"),t("roleId"),t("spaceId"),t("chatId"),t("assistantId"),t("extensionId"),t("toolName"),t("arguments",max=262144),t("result",max=2*1024*1024),t("state"),t("errorCode"),i("createdAt"),i("finishedAt"))
    )
    val tablesV2=tablesV1.map {if(it.name=="roles")it.copy(columns=it.columns+listOf(t("userName"),t("userPersona"),i("personaEnabled")))else it}
    val tablesV3=tablesV2+table("worldbook_entries","id",t("id"),t("roleId"),t("spaceId"),t("chatId",true),t("title"),t("content"),t("trigger"),t("keywords"),i("enabled"),i("priority"),i("version"))
    val tablesV4=tablesV3.map {if(it.name=="roles")it.copy(columns=it.columns+t("conversationMode"))else it}+
        table("relationship_entries","id",t("id"),t("roleId"),t("spaceId"),t("chatId",true),t("sourceName"),t("targetName"),t("label"),t("description"),i("enabled"),i("priority"),i("version"))
    val tablesV5=tablesV4.map {if(it.name=="model_configs")it.copy(columns=it.columns+listOf(i("inputPriceMicrosPerMillion").copy(nullable=true),i("outputPriceMicrosPerMillion").copy(nullable=true),t("priceCurrency")))else it}+
        table("request_usage","requestId",t("requestId"),t("roleId"),t("spaceId"),t("chatId"),t("assistantId"),t("configId"),i("configVersion"),t("provider"),t("model"),i("createdAt"),t("terminalState"),t("errorCode"),t("coverage"),i("modelRounds"),i("reportedRounds"),i("promptTokens").copy(nullable=true),i("completionTokens").copy(nullable=true),i("totalTokens").copy(nullable=true),i("estimatedPromptTokens"),i("estimatedCompletionTokens").copy(nullable=true),i("inputPriceMicrosPerMillion").copy(nullable=true),i("outputPriceMicrosPerMillion").copy(nullable=true),t("currency"),i("inputCostMicros").copy(nullable=true),i("outputCostMicros").copy(nullable=true))
    val tablesV6=tablesV5.map {if(it.name=="model_configs")it.copy(columns=it.columns+i("visionEnabled"))else it}+
        table("message_attachments","messageId",t("messageId"),t("chatId"),t("requestId"),t("mimeType"),i("width"),i("height"),t("sha256"),b("data",max=com.ai.assistance.operit.data.model.MessageImages.MAX_STORED))
    val tablesV7=tablesV6+table("message_materials","messageId",t("messageId"),t("chatId"),t("requestId"),t("kind"),t("displayName",max=com.ai.assistance.operit.data.model.TextMaterials.MAX_NAME*4),t("sourceUrl",true,max=com.ai.assistance.operit.data.model.TextMaterials.MAX_URL),t("mimeType"),t("text",max=com.ai.assistance.operit.data.model.TextMaterials.MAX_TEXT_BYTES),i("originalChars"),i("truncated"),t("sha256"))
    val tablesV8=tablesV7+
        table("sticker_assets","id",t("id"),t("roleId"),t("spaceId"),t("displayName",max=com.ai.assistance.operit.data.model.StickerAssets.MAX_NAME*4),t("mimeType"),i("width"),i("height"),t("sha256"),b("data",max=com.ai.assistance.operit.data.model.StickerAssets.MAX_STORED),i("favorite"),i("createdAt"))+
        table("message_stickers","messageId",t("messageId"),t("chatId"),t("requestId"),t("displayName",max=com.ai.assistance.operit.data.model.StickerAssets.MAX_NAME*4),t("mimeType"),i("width"),i("height"),t("sha256"),b("data",max=com.ai.assistance.operit.data.model.StickerAssets.MAX_STORED))
    val tablesV9=tablesV8+
        table("image_generation_configs","id",t("id"),t("name"),t("endpoint"),t("model"),i("timeoutSeconds"),t("credentialRef"),i("version"),t("size"),i("priceMicros").copy(nullable=true),t("priceCurrency"))+
        table("generated_images","id",t("id"),t("roleId"),t("spaceId"),t("chatId"),t("configId"),i("configVersion"),t("model"),t("prompt",max=com.ai.assistance.operit.data.model.ImageGenerations.MAX_PROMPT*4),t("requestedSize"),i("priceMicros").copy(nullable=true),t("priceCurrency"),t("state"),t("errorCode"),t("mimeType"),i("width"),i("height"),t("sha256"),b("data",true,com.ai.assistance.operit.data.model.ImageGenerations.MAX_STORED),i("createdAt"),i("finishedAt"),i("elapsedMs"))
    val tablesV10=tablesV9+
        table("reading_documents","id",t("id"),t("roleId"),t("spaceId"),t("displayName",max=com.ai.assistance.operit.data.model.ReadingDocuments.MAX_NAME*4),t("mimeType"),t("text",max=com.ai.assistance.operit.data.model.ReadingDocuments.MAX_FILE_BYTES),t("chaptersJson",max=256*1024),i("currentChapter"),i("currentOffset"),t("sha256"),i("version"),i("createdAt"),i("updatedAt"))
    val tablesV11=tablesV10+
        table("memory_entries","id",t("id"),t("roleId"),t("spaceId"),t("kind"),t("title",max=com.ai.assistance.operit.data.model.Memories.TITLE_LIMIT*4),t("content",max=com.ai.assistance.operit.data.model.Memories.CONTENT_LIMIT*4),i("enabled"),i("priority"),t("source"),i("version"),i("createdAt"),i("updatedAt"))
    val tablesV12=tablesV11.map {table->if(table.name=="memory_entries")table.copy(columns=table.columns+listOf(
        t("sourceChatId",true),t("sourceRequestId",true),t("sourceUserMessageId",true),t("sourceAssistantMessageId",true),
        t("sourceModel",true,max=1200),i("sourceConfigVersion").copy(nullable=true),i("sourceMessageAt").copy(nullable=true),i("extractedAt").copy(nullable=true)))else table}
    val tablesV13=tablesV12.map {table->if(table.name=="memory_entries")table.copy(columns=table.columns+listOf(
        t("recallMode"),i("expiresAt").copy(nullable=true),i("forgottenAt").copy(nullable=true),t("replacedById",true)))else table}
    val tablesV14=tablesV13+table("diary_entries","id",t("id"),t("roleId"),t("spaceId"),t("notebook"),t("title",max=com.ai.assistance.operit.data.model.Diaries.TITLE_LIMIT*4),
        t("content",max=com.ai.assistance.operit.data.model.Diaries.CONTENT_LIMIT*4),t("mood"),t("source"),t("sourceModel",true,max=1200),i("sourceConfigVersion").copy(nullable=true),
        t("generationPrompt",true,max=com.ai.assistance.operit.data.model.Diaries.PROMPT_LIMIT*4),i("occurredAt"),i("version"),i("createdAt"),i("updatedAt"))
    val tablesV15=tablesV14+table("environment_profiles","roleId",t("roleId"),t("spaceId"),i("timeContextEnabled"),i("weatherContextEnabled"),t("provider"),
        i("locationId").copy(nullable=true),t("locationName",true,max=640),t("admin1",true,max=640),t("country",true,max=640),t("countryCode",true),
        BackupColumn("latitude",BackupType.REAL,true),BackupColumn("longitude",BackupType.REAL,true),t("timezone",true),t("weatherTime",true),
        BackupColumn("temperature",BackupType.REAL,true),BackupColumn("apparentTemperature",BackupType.REAL,true),i("humidity").copy(nullable=true),
        BackupColumn("precipitation",BackupType.REAL,true),i("weatherCode").copy(nullable=true),BackupColumn("windSpeed",BackupType.REAL,true),
        BackupColumn("todayMin",BackupType.REAL,true),BackupColumn("todayMax",BackupType.REAL,true),i("todayPrecipitationProbability").copy(nullable=true),
        i("fetchedAt").copy(nullable=true),i("version"),i("updatedAt"))
    val tablesV16=tablesV15+
        table("planner_entries","id",t("id"),t("roleId"),t("spaceId"),t("kind"),t("title",max=com.ai.assistance.operit.data.model.PlannerRecords.TITLE_LIMIT*4),t("note",max=com.ai.assistance.operit.data.model.PlannerRecords.NOTE_LIMIT*4),i("startsAt"),i("endsAt").copy(nullable=true),t("zoneId"),i("completedAt").copy(nullable=true),i("reminderMinutesBefore").copy(nullable=true),i("shareWithRole"),i("version"),i("createdAt"),i("updatedAt"))+
        table("cycle_records","id",t("id"),t("roleId"),t("spaceId"),i("startedOn"),i("endedOn").copy(nullable=true),t("flowLevel"),t("note",max=com.ai.assistance.operit.data.model.PlannerRecords.NOTE_LIMIT*4),i("shareWithRole"),i("version"),i("createdAt"),i("updatedAt"))
    val tablesV17=tablesV16+
        table("proactive_role_settings","roleId",t("roleId"),t("spaceId"),i("enabled"),t("chatId"),t("configId"),i("quietStartMinute"),i("quietEndMinute"),i("minIntervalHours"),i("dailyLimit"),i("monthlyBudgetMicros"),t("budgetCurrency"),i("nextEligibleAt"),i("version"),i("updatedAt"))+
        table("proactive_requests","requestId",t("requestId"),t("roleId"),t("spaceId"),t("chatId"),t("assistantId"),t("configId"),i("configVersion"),i("dueAt"),t("dayKey"),t("monthKey"),i("reservedCostMicros"),t("currency"),t("state"),t("errorCode"),i("notificationSent"),i("createdAt"),i("finishedAt"))
    val tablesV18=tablesV17+
        table("speech_service_configs","id",t("id"),t("name"),t("kind"),t("endpoint"),t("model"),t("voice"),i("timeoutSeconds"),t("credentialRef"),i("version"))
    val tablesV19=tablesV18+table("shared_activities","id",t("id"),t("roleId"),t("spaceId"),t("chatId"),t("readingDocumentId"),t("kind"),t("state"),i("chapter"),i("offset"),t("note",max=8000),i("version"),i("createdAt"),i("updatedAt"))
    val tablesV20=tablesV19+listOf(
        table("music_tracks","id",t("id"),t("roleId"),t("spaceId"),t("displayName",max=640),t("mimeType",max=100),i("durationMs"),i("byteCount"),t("sha256"),t("sourceUri",true,16384),i("version"),i("createdAt")),
        table("music_sessions","id",t("id"),t("roleId"),t("spaceId"),t("chatId"),t("trackId"),t("state"),i("positionMs"),t("note",max=8000),t("playbackToken",true,128),i("version"),i("createdAt"),i("updatedAt")))
    val tablesV21=tablesV20.map {if(it.name=="music_tracks")it.copy(columns=it.columns+listOf(t("mediaKind"),BackupColumn("videoWidth",BackupType.INTEGER,true),BackupColumn("videoHeight",BackupType.INTEGER,true)))else it}
    val tablesV22=tablesV21+listOf(table("bilibili_watches","id",t("id"),t("roleId"),t("spaceId"),t("chatId"),t("title",max=640),t("videoId"),i("part"),i("positionMs"),BackupColumn("durationMs",BackupType.INTEGER,true),t("note",max=8000),t("playbackToken",true,128),i("version"),i("createdAt"),i("updatedAt")))
    val tablesV23=tablesV22+listOf(
        table("group_rooms","groupId",t("groupId"),t("spaceId"),t("title"),t("userName"),i("version")),
        table("group_members","id",t("id"),t("groupId"),t("spaceId"),t("roleId"),t("roleSpaceId"),t("name"),t("configId")),
        table("group_requests","requestId",t("requestId"),t("groupId"),t("spaceId"),i("groupVersion"),t("participantId"),t("roleId"),t("roleSpaceId"),i("roleVersion"),t("configId"),i("configVersion"),t("provider"),t("model"),t("userId"),t("assistantId"),i("createdAt"),i("estimatedPromptTokens"),i("inputPriceMicrosPerMillion").copy(nullable=true),i("outputPriceMicrosPerMillion").copy(nullable=true),t("currency"),t("terminalState"),t("errorCode"),i("modelRounds"),i("reportedRounds"),i("promptTokens").copy(nullable=true),i("completionTokens").copy(nullable=true),i("totalTokens").copy(nullable=true),i("estimatedCompletionTokens").copy(nullable=true),i("inputCostMicros").copy(nullable=true),i("outputCostMicros").copy(nullable=true)),
        table("group_messages","id",t("id"),t("groupId"),t("spaceId"),t("requestId"),t("kind"),t("participantId",true),t("speakerName"),t("content",max=524288),t("state"),i("createdAt"),t("errorCode"),i("firstTokenMs"),i("elapsedMs")))
    val tablesV24=tablesV23+listOf(
        table("play_sessions","id",t("id"),t("roleId"),t("spaceId"),t("chatId"),t("title"),t("stateJson",max=131072),i("version"),t("status"),t("requestId",true),i("createdAt"),i("updatedAt")),
        table("play_requests","requestId",t("requestId"),t("activityId"),t("roleId"),t("spaceId"),t("chatId"),i("activityVersion"),i("roleVersion"),t("configId"),i("configVersion"),t("provider"),t("model"),t("assistantId"),t("beforeState",max=131072),t("contextJson",max=524288),t("billingJson",max=4096),i("createdAt"),t("state"),t("content",max=65536),t("errorCode"),i("firstTokenMs"),i("elapsedMs")))
    val tablesV29=tablesV24+listOf(
        table("homes","id",t("id"),t("roleId"),t("spaceId"),t("initialName",max=320),t("stateJson",max=131072),i("version"),i("createdAt"),i("updatedAt")),
        table("home_commands","commandId",t("commandId"),t("homeId"),t("roleId"),t("spaceId"),i("beforeVersion"),t("actionJson",max=4096),t("description",max=1024),i("createdAt")))
    val tablesV30=tablesV29.map {table->if(table.name=="home_commands")table.copy(columns=table.columns.map {
        if(it.name=="actionJson")it.copy(limit=com.ai.assistance.operit.data.model.HomeActionCodec.MAX_BYTES)else it
    })else table}
    // Legacy descriptors are read only for explicit version migration; retired fields are discarded before validation or restore.
    val tablesV31=tablesV30.filter {it.name!="request_usage"}.map {table->
        val removed=com.ai.assistance.operit.data.db.RequestMetadataMigration.removedColumns[table.name].orEmpty()
        table.copy(columns=table.columns.filter {it.name !in removed})
    }
    val tables=tablesV31.filter {it.name !in setOf("homes","home_commands")}+listOf(
        table("home_world","id",t("id"),t("initialJson",max=131072),t("stateJson",max=131072),i("version"),i("createdAt"),i("updatedAt")),
        table("home_events","commandId",t("commandId"),t("homeId"),i("beforeVersion"),t("actionJson",max=com.ai.assistance.operit.data.model.HomeActionCodec.MAX_BYTES),t("description",max=1024),i("createdAt")))
    fun discardUnpublishedHomes(old:List<List<BackupRow>>):List<List<BackupRow>> {
        val source=tablesV31.mapIndexed {index,table->table.name to old[index]}.toMap()
        return tables.map {table->if(table.name in setOf("home_world","home_events"))emptyList() else source.getValue(table.name)}
    }
    fun currentRows(old:List<List<BackupRow>>):List<List<BackupRow>> {
        require(old.size==tablesV30.size)
        val source=tablesV30.mapIndexed {index,table->table.name to old[index]}.toMap()
        return tables.map {table->if(table.name in setOf("home_world","home_events"))emptyList() else source.getValue(table.name).map {row->
            BackupRow(table,table.columns.map {column->row[column.name]})
        }}
    }
    fun upgradeV23(old:List<List<BackupRow>>)=old+listOf(emptyList(),emptyList())
    fun upgradeV22(old:List<List<BackupRow>>)=old+List(4){emptyList<BackupRow>()}
    fun upgradeV21(old:List<List<BackupRow>>)=old+listOf(emptyList())
    fun upgradeV20(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV21[index],it.values+if(it.table.name=="music_tracks")listOf("audio",null,null)else emptyList())}}
    fun upgradeV19(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV20[index],it.values)}}+listOf(emptyList(),emptyList())
    fun upgradeV18(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV19[index],it.values)}}+listOf(emptyList())
    fun upgradeV1(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->
        if(tablesV1[index].name=="roles")rows.map {BackupRow(tablesV2[index],it.values+listOf("","",0L))}else rows
    }
    fun upgradeV3(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->
        if(tablesV3[index].name=="roles")rows.map {BackupRow(tablesV4[index],it.values+"character")}else rows
    }+listOf(emptyList())
    fun upgradeV4(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->
        if(tablesV4[index].name=="model_configs")rows.map {BackupRow(tablesV5[index],it.values+listOf(null,null,"USD"))}else rows
    }+listOf(emptyList())
    fun upgradeV5(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->
        if(tablesV5[index].name=="model_configs")rows.map {BackupRow(tablesV6[index],it.values+0L)}else rows.map {BackupRow(tablesV6[index],it.values)}
    }+listOf(emptyList())
    fun upgradeV6(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV7[index],it.values)}}+listOf(emptyList())
    fun upgradeV7(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV8[index],it.values)}}+listOf(emptyList(),emptyList())
    fun upgradeV8(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV9[index],it.values)}}+listOf(emptyList(),emptyList())
    fun upgradeV9(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV10[index],it.values)}}+listOf(emptyList())
    fun upgradeV10(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV11[index],it.values)}}+listOf(emptyList())
    fun upgradeV11(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {row->
        if(tablesV11[index].name=="memory_entries")BackupRow(tablesV12[index],row.values+List(8){null}) else BackupRow(tablesV12[index],row.values)
    }}
    fun upgradeV12(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {row->
        if(tablesV12[index].name=="memory_entries")BackupRow(tablesV13[index],row.values+listOf("always",null,null,null)) else BackupRow(tablesV13[index],row.values)
    }}
    fun upgradeV13(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV14[index],it.values)}}+listOf(emptyList())
    fun upgradeV14(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV15[index],it.values)}}+listOf(emptyList())
    fun upgradeV15(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV16[index],it.values)}}+listOf(emptyList(),emptyList())
    fun upgradeV16(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV17[index],it.values)}}+listOf(emptyList(),emptyList())
    fun upgradeV17(old:List<List<BackupRow>>)=old.mapIndexed {index,rows->rows.map {BackupRow(tablesV18[index],it.values)}}+listOf(emptyList())
}

/** Envelope v1; payload v32/schema34. Older archives retain supported personal data before restore. */
internal object BackupFormat {
    const val MAX_BYTES=32*1024*1024
    const val MAX_ROWS=100_000
    const val ITERATIONS=600_000
    private val magic="CPCBACK1".toByteArray(Charsets.US_ASCII)
    private const val HEADER=40 // magic + iterations + salt(16) + nonce(12)
    fun read(input:InputStream,checkAccess:()->Unit={}):ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {checkAccess();val n=input.read(buffer);if(n<0)break;if(n==0)continue
            if(out.size().toLong()+n>MAX_BYTES+HEADER+16)throw ChatFailure("BACKUP_LIMIT")
            out.write(buffer,0,n)}
        checkAccess();return out.toByteArray()
    }
    fun checkPassword(password:CharArray) {if(password.size !in 12..128)throw ChatFailure("BACKUP_PASSWORD")}
    private fun key(password:CharArray,salt:ByteArray):ByteArray {
        checkPassword(password)
        val spec=PBEKeySpec(password,salt,ITERATIONS,256)
        return try {SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded}finally {spec.clearPassword()}
    }
    fun encrypt(snapshot:BackupSnapshot,password:CharArray):ByteArray {
        val plain=encode(snapshot)
        val random=SecureRandom();val salt=ByteArray(16).also(random::nextBytes);val nonce=ByteArray(12).also(random::nextBytes)
        val header=ByteBuffer.allocate(HEADER).put(magic).putInt(ITERATIONS).put(salt).put(nonce).array()
        var derived:ByteArray?=null
        try {
            derived=key(password,salt)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(derived,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD(header)
            return header+cipher.doFinal(plain)
        } finally {plain.fill(0);derived?.fill(0)}
    }
    fun decrypt(bytes:ByteArray,password:CharArray):BackupSnapshot {
        if(bytes.size>MAX_BYTES+HEADER+16)throw ChatFailure("BACKUP_LIMIT")
        if(bytes.size<HEADER+16||!bytes.copyOfRange(0,8).contentEquals(magic)||ByteBuffer.wrap(bytes,8,4).int!=ITERATIONS)throw ChatFailure("BACKUP_FORMAT")
        val derived=key(password,bytes.copyOfRange(12,28))
        val plain=try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(derived,"AES"),GCMParameterSpec(128,bytes.copyOfRange(28,40)))
            cipher.updateAAD(bytes,0,HEADER);cipher.doFinal(bytes,HEADER,bytes.size-HEADER)
        } catch(_:java.security.GeneralSecurityException) {throw ChatFailure("BACKUP_AUTH")}
          finally {derived.fill(0)}
        return try {decode(plain)}finally {plain.fill(0)}
    }
    internal fun encode(snapshot:BackupSnapshot):ByteArray {
        val bytes=object:ByteArrayOutputStream() {
            override fun write(b:Int){if(count>=MAX_BYTES)throw ChatFailure("BACKUP_LIMIT");super.write(b)}
            override fun write(b:ByteArray,off:Int,len:Int){if(count.toLong()+len>MAX_BYTES)throw ChatFailure("BACKUP_LIMIT");super.write(b,off,len)}
        }
        val out=DataOutputStream(bytes)
        fun blob(value:ByteArray,limit:Int){require(value.size<=limit);out.writeInt(value.size);out.write(value)}
        fun text(value:String,limit:Int)=blob(value.toByteArray(Charsets.UTF_8),limit)
        out.writeInt(32);out.writeInt(34);out.writeLong(snapshot.createdAt);out.writeBoolean(snapshot.includesCredentials)
        require(snapshot.tables.size==BackupSchema.tables.size&&snapshot.tables.sumOf {it.size}<=MAX_ROWS)
        snapshot.tables.forEachIndexed {index,rows->
            val table=BackupSchema.tables[index];out.writeInt(rows.size)
            rows.forEach {row->require(row.table==table&&row.values.size==table.columns.size)
                table.columns.forEachIndexed {i,c->val value=row.values[i];out.writeBoolean(value!=null)
                    if(value==null)require(c.nullable)else when(c.type){
                        BackupType.TEXT->text(value as String,c.limit)
                        BackupType.INTEGER->out.writeLong(value as Long)
                        BackupType.REAL->out.writeDouble(value as Double)
                        BackupType.BLOB->blob(value as ByteArray,c.limit)
                    }
                }
            }
        }
        require(snapshot.credentials.size<=MAX_ROWS);out.writeInt(snapshot.credentials.size)
        snapshot.credentials.forEach {text(it.ref,128);text(it.endpoint,65536);text(it.key,32768)}
        out.flush();return bytes.toByteArray()
    }
    internal fun decode(bytes:ByteArray):BackupSnapshot=try {
        require(bytes.size<=MAX_BYTES)
        val input=DataInputStream(ByteArrayInputStream(bytes));var count=0
        fun blob(limit:Int):ByteArray {val size=input.readInt();require(size in 0..limit&&size<=input.available());return ByteArray(size).also(input::readFully)}
        fun text(limit:Int):String=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(blob(limit))).toString()
        fun flag():Boolean=when(input.readUnsignedByte()){0->false;1->true;else->error("Invalid flag")}
        val payloadVersion=input.readInt();val schema=input.readInt()
        // v25..v28 add activity kinds to v24's table layout; each kind has an explicit version gate.
        val version=if(payloadVersion in 25..28)24 else payloadVersion
        val specs=when(version to schema){1 to 6->BackupSchema.tablesV1;2 to 7->BackupSchema.tablesV2;3 to 8->BackupSchema.tablesV3;4 to 9->BackupSchema.tablesV4;5 to 10->BackupSchema.tablesV5;6 to 11->BackupSchema.tablesV6;7 to 12->BackupSchema.tablesV7;8 to 13->BackupSchema.tablesV8;9 to 14->BackupSchema.tablesV9;10 to 15->BackupSchema.tablesV10;11 to 16->BackupSchema.tablesV11;12 to 17->BackupSchema.tablesV12;13 to 18->BackupSchema.tablesV13;14 to 19->BackupSchema.tablesV14;15 to 20->BackupSchema.tablesV15;16 to 21->BackupSchema.tablesV16;17 to 22->BackupSchema.tablesV17;18 to 23->BackupSchema.tablesV18;19 to 24->BackupSchema.tablesV19;20 to 25->BackupSchema.tablesV20;21 to 26->BackupSchema.tablesV21;22 to 27->BackupSchema.tablesV22;23 to 28->BackupSchema.tablesV23;24 to 29->BackupSchema.tablesV24;29 to 30->BackupSchema.tablesV29;30 to 30->BackupSchema.tablesV30;31 to 33->BackupSchema.tablesV31;32 to 34->BackupSchema.tables;else->error("Unsupported backup version")}
        val created=input.readLong();require(created>=0);val included=flag()
        val tables=specs.map {table->
            val n=input.readInt();require(n>=0&&n<=MAX_ROWS-count);count+=n
            List(n){BackupRow(table,table.columns.map {c->
                if(!flag()){require(c.nullable);null}else when(c.type){
                    BackupType.TEXT->text(c.limit)
                    BackupType.INTEGER->input.readLong()
                    BackupType.REAL->input.readDouble().also {require(it.isFinite())}
                    BackupType.BLOB->blob(c.limit)
                }
            })}
        }
        val n=input.readInt();require(n in 0..MAX_ROWS)
        val credentials=List(n){BackupCredential(text(128),text(65536),text(32768))}
        require(input.available()==0&&(included||credentials.isEmpty()))
        if(version>=29) {
            val current=when(version){32->tables;31->BackupSchema.discardUnpublishedHomes(tables);else->BackupSchema.currentRows(tables.mapIndexed {i,rows->rows.map {BackupRow(BackupSchema.tablesV30[i],it.values)}})}
            BackupSnapshot(created,current,included,credentials).also {PlayBackup.requireLegacyStates(it,payloadVersion)}
        } else {
        val versionTen=when(version){
            1->BackupSchema.upgradeV9(BackupSchema.upgradeV8(BackupSchema.upgradeV7(BackupSchema.upgradeV6(BackupSchema.upgradeV5(BackupSchema.upgradeV4(BackupSchema.upgradeV3(BackupSchema.upgradeV1(tables)+listOf(emptyList()))))))))
            2->BackupSchema.upgradeV9(BackupSchema.upgradeV8(BackupSchema.upgradeV7(BackupSchema.upgradeV6(BackupSchema.upgradeV5(BackupSchema.upgradeV4(BackupSchema.upgradeV3(tables+listOf(emptyList()))))))))
            3->BackupSchema.upgradeV9(BackupSchema.upgradeV8(BackupSchema.upgradeV7(BackupSchema.upgradeV6(BackupSchema.upgradeV5(BackupSchema.upgradeV4(BackupSchema.upgradeV3(tables)))))))
            4->BackupSchema.upgradeV9(BackupSchema.upgradeV8(BackupSchema.upgradeV7(BackupSchema.upgradeV6(BackupSchema.upgradeV5(BackupSchema.upgradeV4(tables))))))
            5->BackupSchema.upgradeV9(BackupSchema.upgradeV8(BackupSchema.upgradeV7(BackupSchema.upgradeV6(BackupSchema.upgradeV5(tables)))))
            6->BackupSchema.upgradeV9(BackupSchema.upgradeV8(BackupSchema.upgradeV7(BackupSchema.upgradeV6(tables))))
            7->BackupSchema.upgradeV9(BackupSchema.upgradeV8(BackupSchema.upgradeV7(tables)))
            8->BackupSchema.upgradeV9(BackupSchema.upgradeV8(tables))
            9->BackupSchema.upgradeV9(tables)
            10->tables
            11,12,13,14,15,16,17,18,19,20,21,22,23,24->null
            else->error("Unsupported backup version")
        }
        val versionEleven=when(version){11->tables;12,13,14,15,16,17,18,19,20,21,22,23,24->null;else->BackupSchema.upgradeV10(requireNotNull(versionTen))}
        val versionTwelve=when(version){12->tables;13,14,15,16,17,18,19,20,21,22,23,24->null;else->BackupSchema.upgradeV11(requireNotNull(versionEleven))}
        val versionThirteen=when(version){13->tables;14,15,16,17,18,19,20,21,22,23,24->null;else->BackupSchema.upgradeV12(requireNotNull(versionTwelve))}
        val versionFourteen=when(version){14->tables;15,16,17,18,19,20,21,22,23,24->null;else->BackupSchema.upgradeV13(requireNotNull(versionThirteen))}
        val versionFifteen=when(version){15->tables;16,17,18,19,20,21,22,23,24->null;else->BackupSchema.upgradeV14(requireNotNull(versionFourteen))}
        val versionSixteen=when(version){16->tables;17,18,19,20,21,22,23,24->null;else->BackupSchema.upgradeV15(requireNotNull(versionFifteen))}
        val versionSeventeen=when(version){17->tables;18,19,20,21,22,23,24->null;else->BackupSchema.upgradeV16(requireNotNull(versionSixteen))}
        val versionEighteen=when(version){18->tables;19,20,21,22,23,24->null;else->BackupSchema.upgradeV17(requireNotNull(versionSeventeen))}
        val versionNineteen=when(version){19->tables;20,21,22,23,24->null;else->BackupSchema.upgradeV18(requireNotNull(versionEighteen))}
        val versionTwenty=when(version){20->tables;21,22,23,24->null;else->BackupSchema.upgradeV19(requireNotNull(versionNineteen))}
        val versionTwentyOne=if(version>=21)tables else BackupSchema.upgradeV20(requireNotNull(versionTwenty))
        val versionTwentyTwo=if(version>=22)tables else BackupSchema.upgradeV21(versionTwentyOne)
        val versionTwentyThree=if(version>=23)tables else BackupSchema.upgradeV22(versionTwentyTwo)
        val upgraded=if(version==24)tables else BackupSchema.upgradeV23(versionTwentyThree)
        BackupSnapshot(created,BackupSchema.currentRows(upgraded+listOf(emptyList(),emptyList())),included,credentials).also {PlayBackup.requireLegacyStates(it,payloadVersion)}
        }
    }catch(_:Exception){throw ChatFailure("BACKUP_FORMAT")}
}
