package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.PromptTurn
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

object OpenAiMessages {
    const val MAX_JSON_BYTES=12*1024*1024
    fun encode(turns:List<PromptTurn>)=JSONArray().apply {turns.forEach {turn->
        require(turn.images.size<=1&&turn.materials.size<=1&&(turn.images.isEmpty()||turn.role=="user")&&(turn.materials.isEmpty()||turn.role=="user")&&(turn.images.isEmpty()||turn.materials.isEmpty()))
        turn.materials.forEach {it.validate()}
        val text=turn.modelText()
        val content:Any=if(turn.images.isEmpty())text else JSONArray().apply {
            if(text.isNotEmpty())put(JSONObject().put("type","text").put("text",text))
            turn.images.forEach {image->image.validate();put(JSONObject().put("type","image_url").put("image_url",JSONObject()
                .put("url","data:${image.mimeType};base64,${Base64.getEncoder().encodeToString(image.data)}").put("detail","auto")))}
        }
        put(JSONObject().put("role",turn.role).put("content",content))
    }}
    fun checkSize(messages:JSONArray,tools:JSONArray=JSONArray()) {
        if(messages.toString().toByteArray(Charsets.UTF_8).size+tools.toString().toByteArray(Charsets.UTF_8).size>MAX_JSON_BYTES)
            throw com.ai.assistance.operit.data.model.ChatFailure("CONTEXT_LIMIT")
    }
}
