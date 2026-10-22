package com.ai.assistance.operit.data.model

import org.json.JSONObject

/** Version 3 introduces table games while their private rule codecs remain independently versioned. */
data class WordPlayState(val game:WordGuessGame):PlayState {
    override val kind="word_guess"
    override val turn get()=game.turn
    override val result get()=game.result
    override fun observation()=game.observation(PlaySide.ROLE)
    override fun encode()=tableSave(kind,game.encode())
    override fun apply(action:PlayAction):WordPlayState {
        if(action !is PlayAction.Word)throw ChatFailure("PLAY_ACTION")
        return WordPlayState(game.apply(action.move))
    }
}
data class CardPlayState(val game:FourColorGame):PlayState {
    override val kind="four_color"
    override val turn get()=game.turn
    override val result get()=game.result
    override fun observation()=game.observation(PlaySide.ROLE)
    override fun encode()=tableSave(kind,game.encode())
    override fun apply(action:PlayAction):CardPlayState {
        if(action !is PlayAction.Card)throw ChatFailure("PLAY_ACTION")
        return CardPlayState(game.apply(action.move))
    }
}
private fun tableSave(kind:String,state:String)=JSONObject().put("schemaVersion",3).put("kind",kind)
    .put("game",JSONObject(state)).toString().also {require(it.toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)}
