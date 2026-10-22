package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.backup.*
import com.ai.assistance.operit.data.security.CredentialVault
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class HomeStorageAndroidTest {
    @Test fun encryptedBackupImportsOneCompleteWorldOrJoinsResidentsToTheCurrentWorld()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher")
        val names=List(2){"world-backup-${UUID.randomUUID()}.db"}
        val keys=List(2){ByteArray(32).also {SecureRandom().nextBytes(it)}}
        val databases=names.mapIndexed {i,name->Room.databaseBuilder(context,AppDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(keys[i].copyOf())).build()}
        val source=databases[0];val target=databases[1];var now=100L
        try {
            val homes=HomeRepository(source){now};homes.ensure()
            val resident=RoleRepository(source){now}.saveRole(null,"备份居民","合成验收角色","",null)
            homes.submit("backup-plant",HomeAction.Plant(0,HomeCrop.CARROT)){_,_->}
            now=300_100
            homes.submit("backup-harvest",HomeAction.Harvest(0)){_,_->}
            homes.submit("backup-gift",HomeAction.Gift(resident.id,HomeItem.CARROT,1)){_,_->}
            homes.submit("backup-follow",HomeAction.Follow(resident.id)){_,_->}
            val original=homes.current()!!.home.value()
            val vault=CredentialVault(context)
            val password="shared-world-synthetic-password".toCharArray()
            val archive=BackupFormat.encrypt(BackupRepository(source,vault).snapshot(false,{}),password)
            val snapshot=BackupFormat.decrypt(archive,password);password.fill('\u0000')
            val existing=RoleRepository(target).saveRole(null,"原居民","本地合成角色","",null)
            val backups=BackupRepository(target,vault)
            assertEquals(1,backups.restore(snapshot,{},HomeRestoreMode.IMPORT_WORLD).homes)
            val imported=HomeRepository(target).current()!!.home
            val newcomer=target.dao().allRoles().single {it.name=="备份居民"}
            assertNotEquals(resident.id,newcomer.id)
            assertEquals(HomeScope(),imported.scope())
            assertEquals(original.inventory,imported.value().inventory)
            assertEquals(newcomer.id,imported.value().followingRoleId)
            assertEquals(1,imported.value().residents.getValue(newcomer.id).received[HomeItem.CARROT])
            assertTrue(existing.id in imported.value().residents)
            HomeStorageValidation.validate(imported,HomeRepository(target).history(imported.scope()).recent)
            try {backups.restore(snapshot,{},HomeRestoreMode.IMPORT_WORLD);fail("A second world must be rejected")}
            catch(e:ChatFailure){assertEquals("HOME_BACKUP_EXISTS",e.code)}
            assertEquals(3,target.dao().allRoles().size)
            assertEquals(0,backups.restore(snapshot,{},HomeRestoreMode.JOIN_CURRENT).homes)
            val joined=HomeRepository(target).current()!!.home
            assertEquals(5,joined.value().residents.size)
            assertEquals(imported.value().inventory,joined.value().inventory)
            assertEquals(imported.value().plots,joined.value().plots)
            assertEquals(imported.value().player,joined.value().player)
            HomeStorageValidation.validate(joined,HomeRepository(target).history(joined.scope()).recent)
        }finally {
            databases.forEach {it.close()};names.forEach {context.deleteDatabase(it)};keys.forEach {it.fill(0)}
        }
    }

    @Test fun encryptedWorldStartsWithTwoPeopleAndNewRolesJoinWithoutCreatingAnotherWorld()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="shared-home-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open();var now=100L
        try {
            var homes=HomeRepository(db){now};var row=homes.ensure()
            assertEquals(HomeWorld.spawn,row.value().player)
            assertEquals(1,row.value().residents.size);assertEquals(1,db.dao().allRoles().size)
            val original=row.value().residents.keys.single()
            val roles=RoleRepository(db){now};val newcomer=roles.saveRole(null,"新居民","陪伴角色","",null)
            row=homes.current()!!.home
            assertEquals(setOf(original,newcomer.id),row.value().residents.keys)
            val plot=HomeAction.Plant(0,HomeCrop.CARROT)
            val first=homes.apply(row.scope(),row.version,"plant-once",plot)
            assertTrue(homes.apply(row.scope(),row.version,"plant-once",plot).repeated)
            row=first.home
            homes.move(HomeAction.Move(HomeTile(14,15),row.value().residents.mapValues {it.value.tile})){}
            val savedVersion=homes.current()!!.home.version
            val second=homes.submit("plant-after-move",HomeAction.Plant(1,HomeCrop.TOMATO)) {scope,version->
                assertEquals(row.scope(),scope);assertEquals(savedVersion,version)
            }
            assertEquals(savedVersion+1,second.home.version)
            roles.deleteRole(newcomer.scope())
            row=homes.current()!!.home
            assertEquals(setOf(original),row.value().residents.keys)
            assertEquals(HomeCrop.CARROT,row.value().plots[0]!!.crop)
            assertEquals(HomeTile(14,15),row.value().player)
            HomeStorageValidation.validate(row,homes.history(row.scope()).recent)
            val encoded=row.stateJson
            db.close();db=open();homes=HomeRepository(db){now}
            assertEquals(encoded,homes.ensure().stateJson)
            assertEquals(1,homes.current()!!.home.value().residents.size)
        }finally {db.close();context.deleteDatabase(name);key.fill(0)}
    }
}
