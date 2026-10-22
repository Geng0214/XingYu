package com.ai.assistance.operit.data.model

import org.json.JSONObject

data class AdventurePlayState(val game:AdventureGame):PlayState {
    override val kind="pixel_adventure"
    override val turn get()=game.turn
    override val result get()=if(game.ended)PlayResult.ENDED else PlayResult.ACTIVE
    override fun encode()=JSONObject().put("schemaVersion",5).put("kind",kind).put("game",JSONObject(game.encode())).toString()
    override fun observation()=game.observation()
    override fun apply(action:PlayAction):AdventurePlayState=try {
        require(action is PlayAction.Adventure)
        copy(game=game.apply(action.move))
    }catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
}
