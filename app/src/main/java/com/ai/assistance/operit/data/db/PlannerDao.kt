package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    @Query("SELECT p.* FROM planner_entries p JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY p.startsAt,p.id")
    fun observePlanner(roleId:String,spaceId:String):Flow<List<PlannerEntry>>
    @Query("SELECT c.* FROM cycle_records c JOIN roles r ON r.id=c.roleId AND r.spaceId=c.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY c.startedOn DESC,c.id DESC")
    fun observeCycles(roleId:String,spaceId:String):Flow<List<CycleRecord>>
    @Query("SELECT p.* FROM planner_entries p JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY p.startsAt,p.id")
    suspend fun readPlanner(roleId:String,spaceId:String):List<PlannerEntry>
    @Query("SELECT c.* FROM cycle_records c JOIN roles r ON r.id=c.roleId AND r.spaceId=c.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY c.startedOn DESC,c.id DESC")
    suspend fun readCycles(roleId:String,spaceId:String):List<CycleRecord>
    @Query("SELECT p.* FROM planner_entries p JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId WHERE p.id=:id AND r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun planner(roleId:String,spaceId:String,id:String):PlannerEntry?
    @Query("SELECT c.* FROM cycle_records c JOIN roles r ON r.id=c.roleId AND r.spaceId=c.spaceId WHERE c.id=:id AND r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun cycle(roleId:String,spaceId:String,id:String):CycleRecord?
    @Query("SELECT COUNT(*) FROM planner_entries WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun plannerCount(roleId:String,spaceId:String):Int
    @Query("SELECT COUNT(*) FROM cycle_records WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun cycleCount(roleId:String,spaceId:String):Int
    @Insert suspend fun insertPlanner(entry:PlannerEntry)
    @Insert suspend fun insertCycle(record:CycleRecord)
    @Query("UPDATE planner_entries SET kind=:kind,title=:title,note=:note,startsAt=:startsAt,endsAt=:endsAt,zoneId=:zoneId,completedAt=:completedAt,reminderMinutesBefore=:reminderMinutesBefore,shareWithRole=:shareWithRole,version=version+1,updatedAt=:updatedAt WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND version=:expectedVersion AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun updatePlanner(roleId:String,spaceId:String,id:String,kind:String,title:String,note:String,startsAt:Long,endsAt:Long?,zoneId:String,completedAt:Long?,reminderMinutesBefore:Long?,shareWithRole:Boolean,expectedVersion:Long,updatedAt:Long):Int
    @Query("UPDATE cycle_records SET startedOn=:startedOn,endedOn=:endedOn,flowLevel=:flowLevel,note=:note,shareWithRole=:shareWithRole,version=version+1,updatedAt=:updatedAt WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND version=:expectedVersion AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun updateCycle(roleId:String,spaceId:String,id:String,startedOn:Long,endedOn:Long?,flowLevel:String,note:String,shareWithRole:Boolean,expectedVersion:Long,updatedAt:Long):Int
    @Query("DELETE FROM planner_entries WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)") suspend fun deletePlanner(roleId:String,spaceId:String,id:String):Int
    @Query("DELETE FROM cycle_records WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)") suspend fun deleteCycle(roleId:String,spaceId:String,id:String):Int
    @Query("DELETE FROM planner_entries WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun deleteRolePlanner(roleId:String,spaceId:String):Int
    @Query("DELETE FROM cycle_records WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun deleteRoleCycles(roleId:String,spaceId:String):Int
    @Query("SELECT * FROM planner_entries WHERE reminderMinutesBefore IS NOT NULL AND completedAt IS NULL") suspend fun allReminderEntries():List<PlannerEntry>
    @Query("SELECT p.* FROM planner_entries p JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId WHERE p.id=:id LIMIT 1") suspend fun reminderEntry(id:String):PlannerEntry?
}

class PlannerRepository(private val db:AppDatabase) {
    private val roles=RoleRepository(db)
    suspend fun savePlanner(scope:RoleAccessScope,existing:PlannerEntry?,kind:String,title:String,note:String,startsAt:Long,endsAt:Long?,zoneId:String,completedAt:Long?,reminderMinutesBefore:Long?,shareWithRole:Boolean,clock:()->Long={System.currentTimeMillis()}):PlannerEntry=db.withTransaction {
        roles.requireRole(scope);val now=clock();val candidate=PlannerEntry(existing?.id?:UUID.randomUUID().toString(),scope.roleId,scope.spaceId,kind,title,note,startsAt,endsAt,zoneId,completedAt,reminderMinutesBefore,shareWithRole,existing?.version?:1,existing?.createdAt?:now,now.coerceAtLeast((existing?.updatedAt?:-1)+1))
        PlannerRecords.validatePlanner(candidate)
        if(existing==null){if(db.planner().plannerCount(scope.roleId,scope.spaceId)>=PlannerRecords.MAX_PLANNER)throw ChatFailure("PLANNER_LIMIT");db.planner().insertPlanner(candidate);candidate}
        else {if(existing.scope()!=scope)throw ChatFailure("ACCESS");if(db.planner().updatePlanner(scope.roleId,scope.spaceId,existing.id,kind,title,note,startsAt,endsAt,zoneId,completedAt,reminderMinutesBefore,shareWithRole,existing.version,candidate.updatedAt)!=1)throw ChatFailure("PLANNER_CHANGED");candidate.copy(version=existing.version+1)}
    }
    suspend fun saveCycle(scope:RoleAccessScope,existing:CycleRecord?,startedOn:Long,endedOn:Long?,flowLevel:String,note:String,shareWithRole:Boolean,clock:()->Long={System.currentTimeMillis()}):CycleRecord=db.withTransaction {
        roles.requireRole(scope);val now=clock();val candidate=CycleRecord(existing?.id?:UUID.randomUUID().toString(),scope.roleId,scope.spaceId,startedOn,endedOn,flowLevel,note,shareWithRole,existing?.version?:1,existing?.createdAt?:now,now.coerceAtLeast((existing?.updatedAt?:-1)+1))
        PlannerRecords.validateCycle(candidate)
        if(existing==null){if(db.planner().cycleCount(scope.roleId,scope.spaceId)>=PlannerRecords.MAX_CYCLES)throw ChatFailure("CYCLE_LIMIT");db.planner().insertCycle(candidate);candidate}
        else {if(existing.scope()!=scope)throw ChatFailure("ACCESS");if(db.planner().updateCycle(scope.roleId,scope.spaceId,existing.id,startedOn,endedOn,flowLevel,note,shareWithRole,existing.version,candidate.updatedAt)!=1)throw ChatFailure("PLANNER_CHANGED");candidate.copy(version=existing.version+1)}
    }
    suspend fun deletePlanner(scope:RoleAccessScope,id:String)=db.withTransaction {roles.requireRole(scope);if(db.planner().deletePlanner(scope.roleId,scope.spaceId,id)!=1)throw ChatFailure("PLANNER_CHANGED")}
    suspend fun deleteCycle(scope:RoleAccessScope,id:String)=db.withTransaction {roles.requireRole(scope);if(db.planner().deleteCycle(scope.roleId,scope.spaceId,id)!=1)throw ChatFailure("PLANNER_CHANGED")}
}
