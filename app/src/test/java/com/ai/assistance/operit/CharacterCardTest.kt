package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterCardTest {
    private fun v2(name:String="阿岚",description:String="安静的图书管理员。",firstMes:String="你来啦。",lore:JSONArray=JSONArray()):ByteArray {
        val data=JSONObject().put("name",name).put("description",description).put("personality","慢热")
            .put("scenario","小镇图书馆").put("first_mes",firstMes).put("mes_example","<START>")
            .put("character_book",JSONObject().put("entries",lore))
        return JSONObject().put("spec","chara_card_v2").put("spec_version","2.0").put("data",data).toString().toByteArray()
    }

    private fun v1():ByteArray=JSONObject().put("name","旧卡").put("description","平铺字段")
        .put("personality","温和").put("first_mes","你好。").toString().toByteArray()

    private fun entry(content:String="设定",keys:List<String> = listOf("图书馆")):JSONObject {
        val array=JSONArray();keys.forEach(array::put)
        return JSONObject().put("keys",array).put("content",content).put("name","条目").put("insertion_order",30)
    }

    @Test fun readsV2CardWithLore() {
        val lore=JSONArray().put(entry())
        val result=CharacterCards.read(v2(lore=lore))
        assertTrue(result is CharacterCardResult.Success)
        val card=(result as CharacterCardResult.Success).card
        assertEquals("阿岚",card.name)
        assertEquals("你来啦。",card.openingMessage)
        assertTrue(card.prompt.contains("安静的图书管理员。"))
        assertTrue(card.prompt.contains("【性格】"))
        assertEquals(1,card.lore.size)
        assertEquals(listOf("图书馆"),card.lore.first().keywords)
        assertEquals(30,card.lore.first().priority)
    }

    @Test fun readsV1FlatCard() {
        val result=CharacterCards.read(v1())
        assertTrue(result is CharacterCardResult.Success)
        val card=(result as CharacterCardResult.Success).card
        assertEquals("旧卡",card.name)
        assertTrue(card.prompt.contains("平铺字段"))
        assertTrue(card.lore.isEmpty())
    }

    @Test fun rejectsOversizedPromptInsteadOfTruncating() {
        val result=CharacterCards.read(v2(description="字".repeat(CharacterCards.MAX_PROMPT+1)))
        assertTrue(result is CharacterCardResult.Rejected)
        assertTrue((result as CharacterCardResult.Rejected).reason.contains("8000"))
    }

    @Test fun rejectsTooManyLoreEntries() {
        val lore=JSONArray()
        repeat(CharacterCards.MAX_LORE+1){lore.put(entry("第 $it 条"))}
        val result=CharacterCards.read(v2(lore=lore))
        assertTrue(result is CharacterCardResult.Rejected)
        assertTrue((result as CharacterCardResult.Rejected).reason.contains("64"))
    }

    @Test fun rejectsOversizedLoreContentAndTooManyKeywords() {
        val longEntry=CharacterCards.read(v2(lore=JSONArray().put(entry("字".repeat(CharacterCards.MAX_LORE_CONTENT+1)))))
        assertTrue(longEntry is CharacterCardResult.Rejected)
        val manyKeys=(1..CharacterCards.MAX_KEYWORDS+1).map {"k$it"}
        val keyEntry=CharacterCards.read(v2(lore=JSONArray().put(entry(keys=manyKeys))))
        assertTrue(keyEntry is CharacterCardResult.Rejected)
    }

    @Test fun rejectsMalformedAndEmptyCards() {
        assertTrue(CharacterCards.read("not json".toByteArray()) is CharacterCardResult.Rejected)
        assertTrue(CharacterCards.read(JSONObject().put("name","").toString().toByteArray()) is CharacterCardResult.Rejected)
        assertTrue(CharacterCards.read(JSONObject().put("name","空卡").toString().toByteArray()) is CharacterCardResult.Rejected)
        assertTrue(CharacterCards.read(ByteArray(CharacterCards.MAX_BYTES+1)) is CharacterCardResult.Rejected)
    }

    @Test fun rejectsInvalidUtf8() {
        val broken=byteArrayOf(0x7B,0xC3.toByte(),0x28,0x7D)
        assertTrue(CharacterCards.read(broken) is CharacterCardResult.Rejected)
    }
}
