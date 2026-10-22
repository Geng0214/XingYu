package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.main.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HomeRenderFrameTest {
    private val role=RoleAccessScope("resident-1","space-1")
    private val now=1_800_000_000_000L
    private fun home()=HomeState.create("我们的家园",now,listOf(role))
    private fun packet(world:HomeState,time:Long=now,actors:List<HomeRenderActor> = listOf(
        HomeRenderActor("user",14f,14f,true,null,false,1,null),
        HomeRenderActor(role.roleId,13f,14f,false,null,false,-1,HomeTogetherKind.READ)))=
        JSONObject(HomeRenderFrame.packet(world,time,actors,80f,170f,1.2f,HomeWorld.plots.first(),emptyList(),false,false))

    @Test fun rendererUsesActualGeometryAndIdentityWithoutPrivateRoleText() {
        val value=packet(home())
        assertEquals(HomeWorld.WIDTH,value.getJSONObject("geometry").getInt("width"))
        assertEquals(HomeWorld.resources.size,value.getJSONObject("geometry").getJSONArray("resources").length())
        assertEquals(role.roleId,value.getJSONArray("actors").getJSONObject(1).getString("id"))
        assertEquals("READ",value.getJSONArray("actors").getJSONObject(1).getString("activity"))
        assertFalse(value.has("inventory"));assertFalse(value.has("residents"));assertFalse(value.toString().contains("prompt"))
        assertEquals(80.0,value.getJSONObject("camera").getDouble("left"),0.0)
    }

    @Test fun wateredCropAppearanceFollowsAuthoritativeHarvestTime() {
        val initial=home()
        val planted=initial.apply(initial.scope,initial.version,now,HomeAction.Plant(0,HomeCrop.CARROT)).state
        val watered=planted.apply(planted.scope,planted.version,now+1000,HomeAction.Water(0)).state
        val plot=watered.plots[0]!!
        assertTrue(packet(watered,now+1000).getJSONArray("plots").getJSONObject(0).getBoolean("watered"))
        assertEquals(1.0,packet(watered,plot.readyAt).getJSONArray("plots").getJSONObject(0).getDouble("progress"),0.0)
        assertEquals(0.0,packet(planted,now).getJSONArray("plots").getJSONObject(0).getDouble("progress"),0.0)
    }

    @Test fun resourceHarvestChangesRenderAvailability() {
        val initial=home()
        val resource=HomeWorld.resources.first()
        val at=HomeWorld.neighbors(resource.tile).first()
        val moved=initial.apply(initial.scope,initial.version,now,HomeAction.Move(at,initial.residents.mapValues {it.value.tile})).state
        val gathered=moved.apply(moved.scope,moved.version,now+1000,HomeAction.Gather(0)).state
        assertFalse(packet(gathered,now+1000).getJSONArray("resources").getBoolean(0))
        assertTrue(packet(gathered,now+1000+HomeState.RESOURCE_COOLDOWN).getJSONArray("resources").getBoolean(0))
    }

    @Test fun rejectsAmbiguousPlayerOrInvalidCamera() {
        assertThrows(IllegalArgumentException::class.java){HomeRenderFrame.packet(home(),now,emptyList(),0f,0f,1f,null,emptyList(),false,false)}
        assertThrows(IllegalArgumentException::class.java){HomeRenderFrame.packet(home(),now,listOf(HomeRenderActor("user",14f,14f,true,null,false,1,null)),0f,0f,Float.NaN,null,emptyList(),false,false)}
    }

    @Test fun systemAnimationPreferenceReachesSceneWithoutChangingItsWorld() {
        val actors=listOf(HomeRenderActor("user",14f,14f,true,null,false,1,null))
        val reduced=JSONObject(HomeRenderFrame.packet(home(),now,actors,0f,0f,1f,null,emptyList(),false,true))
        assertTrue(reduced.getBoolean("reduceMotion"))
        assertFalse(packet(home()).getBoolean("reduceMotion"))
        assertEquals(home().version,reduced.getLong("version"))
        assertEquals(HomeWorld.plots.size,reduced.getJSONArray("plots").length())
    }

    @Test fun producesExplicitDesktopFixtureFromSamePacket() {
        val root=File(requireNotNull(System.getProperty("companion.repo")))
        val fixture=File(root,"build/e15-home-fixture.json")
        requireNotNull(fixture.parentFile).mkdirs();fixture.writeText(packet(home()).toString())
        assertEquals(homeAvatarTile("user",true),packet(home()).getJSONArray("actors").getJSONObject(0).getInt("sprite"))
    }
}
