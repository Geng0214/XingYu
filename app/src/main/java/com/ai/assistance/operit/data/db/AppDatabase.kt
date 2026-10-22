package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.CredentialVault
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID

class Converters {
    @TypeConverter fun state(value: String): MessageState = MessageState.valueOf(value)
    @TypeConverter fun state(value: MessageState): String = value.name
}

// Content queries join the owning role before returning any rows.
@Dao
interface CompanionDao {
    @Query("SELECT p.* FROM role_portraits p JOIN roles r ON p.roleId=r.id AND p.spaceId=r.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId AND p.kind=:kind")
    fun portrait(roleId: String, spaceId: String, kind: String): Flow<RolePortrait?>
    @Upsert suspend fun savePortrait(portrait: RolePortrait)
    @Query("DELETE FROM role_portraits WHERE roleId=:roleId AND spaceId=:spaceId AND kind=:kind")
    suspend fun deletePortrait(roleId: String, spaceId: String, kind: String)
    @Query("DELETE FROM role_portraits WHERE roleId=:roleId AND spaceId=:spaceId")
    suspend fun deletePortraits(roleId: String, spaceId: String)
    @Query("UPDATE chats SET pinned=:pinned WHERE id=:chatId AND roleId=:roleId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun setPinned(roleId: String, spaceId: String, chatId: String, pinned: Boolean): Int
    @Query("UPDATE messages SET favorite=:favorite WHERE id=:messageId AND chatId=:chatId AND content!='' AND state NOT IN ('QUEUED','WAITING','GENERATING') AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId)")
    suspend fun setFavorite(roleId: String, spaceId: String, chatId: String, messageId: String, favorite: Boolean): Int
    @Query("SELECT m.* FROM messages m JOIN chats c ON m.chatId=c.id JOIN roles r ON c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId AND m.content!='' AND (:favoritesOnly=0 OR m.favorite=1) AND instr(lower(m.content),lower(:query))>0 ORDER BY m.createdAt DESC,m.id DESC LIMIT :limit OFFSET :offset")
    fun searchMessages(roleId: String, spaceId: String, query: String, favoritesOnly: Boolean, limit: Int, offset: Int): Flow<List<MessageEntity>>
    // The inbox is user-facing metadata. Model context still requires a specific role scope.
    // Opening text is a visible timeline event stored on the chat, not in messages.
    // Include that event here so a newly opened conversation has the same preview as its timeline.
    @Query("""SELECT c.id AS chatId,c.roleId,r.spaceId,r.name AS roleName,c.title,c.pinned,
        (SELECT preview FROM (
            SELECT m.createdAt AS at,m.id AS tie,CASE WHEN m.content!='' THEN substr(m.content,1,100)
                WHEN EXISTS (SELECT 1 FROM message_stickers s WHERE s.messageId=m.id) THEN '[表情]'
                WHEN EXISTS (SELECT 1 FROM message_attachments a WHERE a.messageId=m.id) THEN '[图片]' ELSE '[资料]' END AS preview
            FROM messages m WHERE m.chatId=c.id AND (m.content!=''
                OR EXISTS (SELECT 1 FROM message_stickers s WHERE s.messageId=m.id)
                OR EXISTS (SELECT 1 FROM message_attachments a WHERE a.messageId=m.id)
                OR EXISTS (SELECT 1 FROM message_materials t WHERE t.messageId=m.id))
            UNION ALL SELECT g.createdAt AS at,g.id AS tie,'[生图] '||substr(g.prompt,1,90) AS preview FROM generated_images g WHERE g.chatId=c.id
            UNION ALL SELECT c.createdAt AS at,'' AS tie,substr(c.openingMessage,1,100) AS preview WHERE c.openingMessage!=''
        ) ORDER BY at DESC,tie DESC LIMIT 1) AS preview,
        (SELECT MAX(at) FROM (
            SELECT m.createdAt AS at FROM messages m WHERE m.chatId=c.id
            UNION ALL SELECT g.createdAt AS at FROM generated_images g WHERE g.chatId=c.id
            UNION ALL SELECT c.createdAt AS at WHERE c.openingMessage!=''
        )) AS updatedAt,c.createdAt FROM chats c JOIN roles r ON r.id=c.roleId
        ORDER BY c.pinned DESC,IFNULL(updatedAt,c.createdAt) DESC,c.id DESC""")
    fun inbox(): Flow<List<ConversationCard>>
    @Query("SELECT a.* FROM role_appearance a JOIN roles r ON a.roleId=r.id AND a.spaceId=r.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId")
    fun appearance(roleId: String, spaceId: String): Flow<ChatAppearance?>
    @Query("SELECT a.* FROM role_appearance a JOIN roles r ON a.roleId=r.id AND a.spaceId=r.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId")
    suspend fun readAppearance(roleId: String, spaceId: String): ChatAppearance?
    @Upsert suspend fun saveAppearance(appearance: ChatAppearance)
    @Query("DELETE FROM role_appearance WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun deleteAppearance(roleId: String, spaceId: String)
    @Query("SELECT * FROM roles ORDER BY name") fun roles(): Flow<List<RoleEntity>>
    @Query("SELECT * FROM roles ORDER BY name,id") suspend fun allRoles(): List<RoleEntity>
    @Query("SELECT * FROM model_configs ORDER BY name") fun configs(): Flow<List<ModelConfigData>>
    @Query("SELECT * FROM image_generation_configs ORDER BY name,id") fun imageGenerationConfigs():Flow<List<ImageGenerationConfig>>
    @Query("SELECT * FROM image_generation_configs WHERE id=:id LIMIT 1") suspend fun imageGenerationConfig(id:String):ImageGenerationConfig?
    @Upsert suspend fun saveImageGenerationConfig(config:ImageGenerationConfig)
    @Query("SELECT * FROM speech_service_configs ORDER BY kind,name,id") fun speechServiceConfigs():Flow<List<SpeechServiceConfig>>
    @Query("SELECT * FROM speech_service_configs WHERE id=:id LIMIT 1") suspend fun speechServiceConfig(id:String):SpeechServiceConfig?
    @Upsert suspend fun saveSpeechServiceConfig(config:SpeechServiceConfig)
    @Query("SELECT COUNT(*) FROM messages WHERE kind='assistant' AND state='COMPLETE' AND content!=''") fun completedReplyCount():Flow<Int>
    @Query("SELECT c.* FROM chats c JOIN roles r ON c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY c.createdAt DESC")
    fun chats(roleId: String, spaceId: String): Flow<List<ChatEntity>>
    @Query("SELECT c.* FROM chats c JOIN roles r ON c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun privateChat(roleId: String, spaceId: String): ChatEntity?
    @Query("SELECT m.* FROM messages m JOIN chats c ON m.chatId=c.id JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId ORDER BY m.createdAt,m.id")
    fun messages(roleId: String, spaceId: String, chatId: String): Flow<List<MessageEntity>>
    @Query("SELECT m.* FROM messages m JOIN chats c ON m.chatId=c.id JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId ORDER BY m.createdAt,m.id")
    suspend fun history(roleId: String, spaceId: String, chatId: String): List<MessageEntity>
    @Query("SELECT m.* FROM messages m JOIN chats c ON m.chatId=c.id JOIN roles r ON c.roleId=r.id WHERE m.id=:messageId AND c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun message(roleId:String,spaceId:String,chatId:String,messageId:String):MessageEntity?
    @Query("SELECT a.* FROM message_attachments a JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' ORDER BY m.createdAt,m.id")
    fun attachments(roleId:String,spaceId:String,chatId:String):Flow<List<MessageAttachment>>
    @Query("SELECT a.* FROM message_attachments a JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' ORDER BY m.createdAt,m.id")
    suspend fun historyAttachments(roleId:String,spaceId:String,chatId:String):List<MessageAttachment>
    @Query("SELECT a.* FROM message_attachments a JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' AND m.requestId=:requestId LIMIT 1")
    suspend fun requestAttachment(roleId:String,spaceId:String,chatId:String,requestId:String):MessageAttachment?
    @Query("SELECT a.* FROM message_materials a JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' ORDER BY m.createdAt,m.id")
    fun materials(roleId:String,spaceId:String,chatId:String):Flow<List<MessageMaterial>>
    @Query("SELECT a.* FROM message_materials a JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' ORDER BY m.createdAt,m.id")
    suspend fun historyMaterials(roleId:String,spaceId:String,chatId:String):List<MessageMaterial>
    @Query("SELECT a.* FROM message_materials a JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' AND m.requestId=:requestId LIMIT 1")
    suspend fun requestMaterial(roleId:String,spaceId:String,chatId:String,requestId:String):MessageMaterial?
    @Query("SELECT d.* FROM reading_documents d JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY d.updatedAt DESC,d.id DESC")
    fun readingDocuments(roleId:String,spaceId:String):Flow<List<ReadingDocument>>
    @Query("SELECT d.* FROM reading_documents d JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE d.id=:id AND r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun readingDocument(roleId:String,spaceId:String,id:String):ReadingDocument?
    @Query("SELECT COUNT(*) FROM reading_documents d JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId")
    suspend fun readingDocumentCount(roleId:String,spaceId:String):Int
    @Insert suspend fun insertReadingDocument(document:ReadingDocument)
    @Query("UPDATE reading_documents SET currentChapter=:chapter,currentOffset=:offset,version=version+1,updatedAt=:updatedAt WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun updateReadingProgress(roleId:String,spaceId:String,id:String,chapter:Int,offset:Int,updatedAt:Long):Int
    @Query("SELECT COUNT(*) FROM message_materials WHERE kind='reading' AND sourceUrl LIKE 'reading:'||:documentId||':%'")
    suspend fun readingReferenceCount(documentId:String):Int
    @Query("DELETE FROM reading_documents WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun deleteReadingDocument(roleId:String,spaceId:String,id:String):Int
    @Query("SELECT s.* FROM sticker_assets s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY s.favorite DESC,s.createdAt DESC,s.id DESC")
    fun stickerAssets(roleId:String,spaceId:String):Flow<List<StickerAsset>>
    @Query("SELECT s.* FROM sticker_assets s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE s.id=:id AND r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun stickerAsset(roleId:String,spaceId:String,id:String):StickerAsset?
    @Query("SELECT COUNT(*) FROM sticker_assets s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId")
    suspend fun stickerAssetCount(roleId:String,spaceId:String):Int
    @Insert suspend fun insertStickerAsset(asset:StickerAsset)
    @Query("UPDATE sticker_assets SET favorite=:favorite WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun setStickerFavorite(roleId:String,spaceId:String,id:String,favorite:Boolean):Int
    @Query("DELETE FROM sticker_assets WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun deleteStickerAsset(roleId:String,spaceId:String,id:String):Int
    @Query("SELECT s.* FROM message_stickers s JOIN messages m ON m.id=s.messageId AND m.chatId=s.chatId AND m.requestId=s.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' ORDER BY m.createdAt,m.id")
    fun messageStickers(roleId:String,spaceId:String,chatId:String):Flow<List<MessageSticker>>
    @Query("SELECT s.* FROM message_stickers s JOIN messages m ON m.id=s.messageId AND m.chatId=s.chatId AND m.requestId=s.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' ORDER BY m.createdAt,m.id")
    suspend fun historyStickers(roleId:String,spaceId:String,chatId:String):List<MessageSticker>
    @Query("SELECT s.* FROM message_stickers s JOIN messages m ON m.id=s.messageId AND m.chatId=s.chatId AND m.requestId=s.requestId JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.kind='user' AND m.requestId=:requestId LIMIT 1")
    suspend fun requestSticker(roleId:String,spaceId:String,chatId:String,requestId:String):MessageSticker?
    @Query("SELECT g.* FROM generated_images g JOIN chats c ON c.id=g.chatId JOIN roles r ON r.id=c.roleId WHERE g.chatId=:chatId AND g.roleId=:roleId AND g.spaceId=:spaceId AND c.roleId=:roleId AND r.spaceId=:spaceId ORDER BY g.createdAt,g.id")
    fun generatedImages(roleId:String,spaceId:String,chatId:String):Flow<List<GeneratedImage>>
    @Insert suspend fun insertGeneratedImage(image:GeneratedImage)
    @Query("UPDATE generated_images SET state='GENERATING' WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId AND state='QUEUED' AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND c.roleId=:roleId AND r.spaceId=:spaceId)")
    suspend fun startGeneratedImage(roleId:String,spaceId:String,chatId:String,id:String):Int
    @Query("UPDATE generated_images SET state='COMPLETE',errorCode='',mimeType=:mimeType,width=:width,height=:height,sha256=:sha256,data=:data,finishedAt=:finishedAt,elapsedMs=:elapsedMs WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId AND state IN ('QUEUED','WAITING','GENERATING') AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND c.roleId=:roleId AND r.spaceId=:spaceId)")
    suspend fun completeGeneratedImage(roleId:String,spaceId:String,chatId:String,id:String,mimeType:String,width:Int,height:Int,sha256:String,data:ByteArray,finishedAt:Long,elapsedMs:Long):Int
    @Query("UPDATE generated_images SET state=:state,errorCode=:error,finishedAt=:finishedAt,elapsedMs=:elapsedMs WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId AND state IN ('QUEUED','WAITING','GENERATING') AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON r.id=c.roleId WHERE c.id=:chatId AND c.roleId=:roleId AND r.spaceId=:spaceId)")
    suspend fun finishGeneratedImage(roleId:String,spaceId:String,chatId:String,id:String,state:MessageState,error:String,finishedAt:Long,elapsedMs:Long):Int
    @Query("SELECT c.* FROM chats c JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId")
    suspend fun chat(roleId: String, spaceId: String, chatId: String): ChatEntity?
    @Query("SELECT * FROM roles WHERE id=:id") suspend fun role(id: String): RoleEntity?
    @Query("SELECT * FROM model_configs WHERE id=:id") suspend fun config(id: String): ModelConfigData?
    @Upsert suspend fun saveRole(role: RoleEntity)
    @Upsert suspend fun saveConfig(config: ModelConfigData)
    @Query("""SELECT (SELECT COUNT(*) FROM roles WHERE defaultConfigId=:id)
        +(SELECT COUNT(*) FROM chats WHERE configId=:id)
        +(SELECT COUNT(*) FROM group_members WHERE configId=:id)
        +(SELECT COUNT(*) FROM group_requests WHERE configId=:id)
        +(SELECT COUNT(*) FROM play_requests WHERE configId=:id)
        +(SELECT COUNT(*) FROM proactive_role_settings WHERE configId=:id)
        +(SELECT COUNT(*) FROM proactive_requests WHERE configId=:id)""")
    suspend fun configReferenceCount(id:String):Int
    @Query("DELETE FROM model_configs WHERE id=:id AND version=:version")
    suspend fun deleteConfig(id:String,version:Long):Int
    @Insert suspend fun insertChat(chat: ChatEntity)
    @Insert suspend fun insertMessages(messages: List<MessageEntity>)
    @Insert suspend fun insertAttachment(attachment:MessageAttachment)
    @Insert suspend fun insertMaterial(material:MessageMaterial)
    @Insert suspend fun insertMessageSticker(sticker:MessageSticker)
    @Query("UPDATE messages SET content=:text,state=:state,errorCode=:error,firstTokenMs=:first,elapsedMs=:elapsed WHERE id=:id AND chatId=:chatId AND requestId=:requestId AND kind='assistant' AND state IN ('QUEUED','WAITING','GENERATING') AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId)")
    suspend fun updateActive(roleId: String, spaceId: String, chatId: String, requestId: String, id: String,
        text: String, state: MessageState, error: String, first: Long, elapsed: Long): Int
    @Query("UPDATE messages SET state='FAILED',errorCode='INTERRUPTED' WHERE kind='assistant' AND state IN ('QUEUED','WAITING','GENERATING')")
    suspend fun recoverInterrupted(): Int
    @Query("""DELETE FROM messages WHERE id=:id AND requestId=:requestId AND chatId=:chatId AND kind='assistant' AND state IN ('QUEUED','WAITING','GENERATING')
        AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId)""")
    suspend fun deleteActive(roleId:String,spaceId:String,chatId:String,requestId:String,id:String):Int
    @Query("UPDATE generated_images SET state='FAILED',errorCode='INTERRUPTED',finishedAt=:now,elapsedMs=CASE WHEN createdAt<=:now THEN :now-createdAt ELSE 0 END WHERE state IN ('QUEUED','WAITING','GENERATING')")
    suspend fun recoverInterruptedImages(now:Long):Int
    @Query("DELETE FROM messages WHERE chatId=:chatId AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId)")
    suspend fun deleteMessages(roleId: String, spaceId: String, chatId: String)
    @Query("DELETE FROM messages WHERE id IN (:messageIds) AND chatId=:chatId AND state NOT IN ('QUEUED','WAITING','GENERATING') AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON c.roleId=r.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId)")
    suspend fun deleteSelectedMessages(roleId:String,spaceId:String,chatId:String,messageIds:List<String>):Int
    @Query("DELETE FROM chats WHERE id=:chatId AND roleId=:roleId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun deleteChat(roleId: String, spaceId: String, chatId: String)
    @Query("SELECT COUNT(*) FROM chats c JOIN roles r ON c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId")
    suspend fun chatCount(roleId: String, spaceId: String): Int
    @Query("DELETE FROM messages WHERE chatId IN (SELECT c.id FROM chats c JOIN roles r ON c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId)")
    suspend fun deleteRoleMessages(roleId: String, spaceId: String)
    @Query("DELETE FROM chats WHERE roleId=:roleId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun deleteRoleChats(roleId: String, spaceId: String)
    @Query("DELETE FROM roles WHERE id=:roleId AND spaceId=:spaceId") suspend fun deleteRole(roleId: String, spaceId: String)
}

@Database(entities = [HomeRow::class,HomeCommandRow::class,PlaySessionRow::class,PlayRequestRow::class,GroupRoomRow::class,GroupMemberRow::class,GroupRequestRow::class,GroupMessageRow::class,RoleEntity::class, ModelConfigData::class,ImageGenerationConfig::class,GeneratedImage::class,SpeechServiceConfig::class,ChatEntity::class, MessageEntity::class, ChatAppearance::class, RolePortrait::class,
    BilibiliWatch::class,MusicTrack::class,MusicSession::class,SharedActivity::class,MessageAttachment::class,MessageMaterial::class,ReadingDocument::class,StickerAsset::class,MessageSticker::class,McpExtension::class,ExtensionTool::class,RoleToolGrant::class,RequestToolPermission::class,ToolInvocationRecord::class,ToolPkgRecord::class,WorldbookEntry::class,RelationshipEntry::class,MemoryEntry::class,DiaryEntry::class,EnvironmentProfile::class,PlannerEntry::class,CycleRecord::class,ProactiveRoleSetting::class,ProactiveRequestEntity::class], version = 34, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): CompanionDao
    abstract fun extensions():ExtensionDao
    abstract fun toolpkgs():ToolPkgDao
    abstract fun worldbook():WorldbookDao
    abstract fun relationships():RelationshipDao
    abstract fun memories():MemoryDao
    abstract fun diaries():DiaryDao
    abstract fun environment():EnvironmentDao
    abstract fun planner():PlannerDao
    abstract fun proactive():ProactiveDao
    abstract fun activities():SharedActivityDao
    abstract fun music():MusicDao
    abstract fun bilibili():BilibiliDao
    abstract fun groups():GroupDao
    abstract fun play():PlayDao
    abstract fun home():HomeDao
    suspend fun recoverInterruptedRequests()=withTransaction {
        music().recover()
        bilibili().recover()
        GroupRepository(this@AppDatabase).recover()
        PlayRepository(this@AppDatabase).recover()
        extensions().recoverInterrupted(System.currentTimeMillis())
        dao().recoverInterrupted()
        dao().recoverInterruptedImages(System.currentTimeMillis())
        proactive().recoverInterrupted(System.currentTimeMillis())
    }
    companion object {
        val MIGRATION_4_5=ExtensionMigration
        val MIGRATION_5_6=ToolPkgMigration
        val MIGRATION_6_7=PersonaMigration
        val MIGRATION_7_8=WorldbookMigration
        val MIGRATION_8_9=RelationshipMigration
        val MIGRATION_9_10=UsageMigration
        val MIGRATION_10_11=AttachmentMigration
        val MIGRATION_11_12=MaterialMigration
        val MIGRATION_12_13=StickerMigration
        val MIGRATION_13_14=ImageGenerationMigration
        val MIGRATION_14_15=ReadingMigration
        val MIGRATION_15_16=MemoryMigration
        val MIGRATION_16_17=MemorySourceMigration
        val MIGRATION_17_18=MemoryRecallMigration
        val MIGRATION_18_19=DiaryMigration
        val MIGRATION_19_20=EnvironmentMigration
        val MIGRATION_20_21=PlannerMigration
        val MIGRATION_21_22=ProactiveMigration
        val MIGRATION_22_23=SpeechMigration
        val MIGRATION_23_24=SharedActivityMigration
        val MIGRATION_24_25=MusicMigration
        val MIGRATION_25_26=VideoMigration
        val MIGRATION_26_27=BilibiliMigration
        val MIGRATION_27_28=GroupMigration
        val MIGRATION_28_29=PlayMigration
        // Schema 30's unpublished home feature has been removed; schema 34 creates the shared world tables.
        val MIGRATION_29_30=object:Migration(29,30) {override fun migrate(db:SupportSQLiteDatabase) {}}
        val MIGRATION_30_31=ConversationMessagesMigration
        val MIGRATION_31_32=SingleConversationMigration
        val MIGRATION_32_33=RequestMetadataMigration
        val MIGRATION_33_34=SharedHomeMigration
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("SELECT (SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON c.roleId=r.id LEFT JOIN model_configs m ON c.configId=m.id WHERE r.id IS NULL OR m.id IS NULL) + (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON m.chatId=c.id WHERE c.id IS NULL) + (SELECT COUNT(*) FROM role_appearance a LEFT JOIN roles r ON a.roleId=r.id AND a.spaceId=r.spaceId WHERE r.id IS NULL)").use {
                    check(it.moveToFirst() && it.getLong(0)==0L) { "Invalid ownership before upgrade" }
                }
                db.execSQL("ALTER TABLE chats ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS role_portraits (roleId TEXT NOT NULL, spaceId TEXT NOT NULL, kind TEXT NOT NULL, image BLOB NOT NULL, PRIMARY KEY(roleId,kind))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE roles ADD COLUMN replyStyle TEXT NOT NULL DEFAULT 'short'")
                db.execSQL("ALTER TABLE messages ADD COLUMN bubbleStyle TEXT NOT NULL DEFAULT 'paragraph'")
                db.execSQL("CREATE TABLE IF NOT EXISTS role_appearance (roleId TEXT NOT NULL, spaceId TEXT NOT NULL, wallpaper BLOB, dimming REAL NOT NULL, palette TEXT NOT NULL, PRIMARY KEY(roleId))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room migrates transactionally. Reject broken ownership before altering anything.
                db.query("SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON c.roleId=r.id LEFT JOIN model_configs m ON c.configId=m.id WHERE r.id IS NULL OR m.id IS NULL").use {
                    check(it.moveToFirst() && it.getLong(0)==0L) { "Invalid chat ownership" }
                }
                db.query("SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON m.chatId=c.id WHERE c.id IS NULL").use {
                    check(it.moveToFirst() && it.getLong(0)==0L) { "Invalid message ownership" }
                }
                db.execSQL("ALTER TABLE roles ADD COLUMN spaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE roles ADD COLUMN openingMessage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE roles ADD COLUMN defaultConfigId TEXT")
                val ids = mutableListOf<String>()
                db.query("SELECT id FROM roles").use { while(it.moveToNext()) ids.add(it.getString(0)) }
                ids.forEach { db.execSQL("UPDATE roles SET spaceId=? WHERE id=?", arrayOf(UUID.randomUUID().toString(), it)) }
                db.execSQL("CREATE UNIQUE INDEX index_roles_spaceId ON roles(spaceId)")
                db.execSQL("ALTER TABLE chats ADD COLUMN openingMessage TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context, vault: CredentialVault): AppDatabase {
            System.loadLibrary("sqlcipher")
            val key = vault.databaseKey(context.getDatabasePath("companion.db").exists())
            return Room.databaseBuilder(context, AppDatabase::class.java, "companion.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Seed only on creation; a deliberately deleted role must stay deleted.
                        db.execSQL("INSERT INTO roles(id,name,prompt,version,spaceId,openingMessage,defaultConfigId) VALUES(?,?,?,1,?,'',NULL)",
                            arrayOf(UUID.randomUUID().toString(), "初见", "你是一位温柔、真诚的 AI 陪伴者。认真倾听，以自然简洁的中文交流。尊重用户的现实生活与选择，不假装自己是真人。", UUID.randomUUID().toString()))
                    }
                }).build()
        }
    }
}
