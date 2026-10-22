package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.*

/** Authored pixel pictures share the farm's visual vocabulary, including actual inventory products. */
internal enum class HomeSymbol { BAG,TALK,GIFT,FOLLOW,WATER,HARVEST,AXE,FISH,CRAFT,DOOR,MAIL,WARDROBE,DECORATE,COIN,ENERGY,HEART,TEA,READ,CLEAN,MEAL,WALK,REST,SEED,SELL,TRASH,CHECK }

@Composable internal fun HomeItemPicture(item:HomeItem,modifier:Modifier) {
    Canvas(modifier){withTransform({scale(size.width/32,size.height/32,pivot=Offset.Zero)}){HomePictures.run {item(item)}}}
}
@Composable internal fun HomeSymbolPicture(symbol:HomeSymbol,modifier:Modifier) {
    Canvas(modifier){withTransform({scale(size.width/32,size.height/32,pivot=Offset.Zero)}){HomePictures.run {symbol(symbol)}}}
}
@Composable internal fun HomePortrait(identity:String,user:Boolean,outfit:HomeItem?,modifier:Modifier) {
    val atlas=ImageBitmap.imageResource(if(user)R.drawable.home_farm_atlas else R.drawable.home_people_atlas)
    val tile=homeAvatarTile(identity,user)
    Canvas(modifier){withTransform({scale(size.width/48,size.height/56,pivot=Offset.Zero)}){
        drawImage(atlas,srcOffset=IntOffset(tile%12*16,tile/12*16),srcSize=IntSize(16,16),dstOffset=IntOffset(4,9),dstSize=IntSize(40,40),filterQuality=androidx.compose.ui.graphics.FilterQuality.None)
        when(outfit) {
            HomeItem.SUN_HAT->{drawRect(Color(0xFFE8C47A),Offset(9f,9f),Size(30f,5f));drawRect(Color(0xFFE8C47A),Offset(15f,3f),Size(18f,9f))}
            HomeItem.APRON->drawRect(Color(0xFFF4E8CA),Offset(17f,32f),Size(14f,11f))
            HomeItem.GARDEN_SUIT->drawRect(Color(0xFF6D9875),Offset(15f,31f),Size(18f,12f))
            else->Unit
        }
    }}
}
internal fun homeActivityPicture(kind:HomeTogetherKind)=when(kind) {
    HomeTogetherKind.TEA->HomeSymbol.TEA;HomeTogetherKind.READ->HomeSymbol.READ;HomeTogetherKind.CLEAN->HomeSymbol.CLEAN
    HomeTogetherKind.MEAL->HomeSymbol.MEAL;HomeTogetherKind.WALK->HomeSymbol.WALK;HomeTogetherKind.REST->HomeSymbol.REST
}
internal object HomePictures {
    private val ink=Color(0xFF3E493C)
    private val cream=Color(0xFFF7EAC7)
    private val green=Color(0xFF668A4A)
    private val gold=Color(0xFFE3B453)
    private val wood=Color(0xFF946044)
    private val red=Color(0xFFD97760)
    private val blue=Color(0xFF79A7AB)
    private fun DrawScope.p(c:Color,x:Int,y:Int,w:Int,h:Int)=drawRect(c,Offset(x.toFloat(),y.toFloat()),Size(w.toFloat(),h.toFloat()))
    private fun DrawScope.box(c:Color,x:Int,y:Int,w:Int,h:Int){p(ink,x,y,w,h);p(c,x+2,y+2,w-4,h-4)}
    fun DrawScope.item(item:HomeItem) {
        when(item) {
            HomeItem.CARROT_SEED,HomeItem.TOMATO_SEED,HomeItem.COTTON_SEED->{
                box(cream,6,4,20,25);p(gold,8,4,16,4)
                withTransform({translate(7f,9f);scale(.6f,.6f,pivot=Offset.Zero)}){
                    item(when(item){HomeItem.CARROT_SEED->HomeItem.CARROT;HomeItem.TOMATO_SEED->HomeItem.TOMATO;else->HomeItem.COTTON})
                }
            }
            HomeItem.CARROT->{p(green,13,3,4,8);p(green,6,3,7,4);p(green,17,1,7,4);p(wood,9,11,15,7);p(Color(0xFFF0A14C),10,10,13,7);p(Color(0xFFDA8740),12,17,9,6);p(Color(0xFFDA8740),14,23,5,4);p(cream,12,12,3,2)}
            HomeItem.TOMATO,HomeItem.BERRY->{val c=if(item==HomeItem.TOMATO)red else Color(0xFF9E607A);p(ink,6,10,21,14);p(c,4,12,24,10);p(c,8,7,16,20);p(green,13,4,4,7);p(green,9,7,14,3);p(cream,9,12,4,3)}
            HomeItem.COTTON->{p(green,14,17,4,13);p(green,7,25,9,3);p(green,18,22,8,3);p(Color(0xFFD9CBA7),5,8,23,14);p(cream,6,5,10,16);p(cream,14,3,11,17);p(Color(0xFFFFF8E2),10,7,13,12)}
            HomeItem.CLOTH->{box(blue,4,8,23,20);box(cream,8,5,20,18);p(blue,12,9,12,2);p(blue,12,14,12,2);p(gold,20,7,3,13)}
            HomeItem.CARROT_SOUP,HomeItem.TOMATO_SOUP,HomeItem.SALAD->{p(ink,4,12,24,12);p(cream,6,14,20,11);p(if(item==HomeItem.SALAD)green else if(item==HomeItem.CARROT_SOUP)gold else red,6,10,20,8);p(cream,4,17,24,3);p(green,10,11,4,3);p(gold,20,12,3,2)}
            HomeItem.APRON,HomeItem.GARDEN_SUIT->{p(ink,11,3,10,7);p(if(item==HomeItem.APRON)cream else green,7,10,18,19);p(if(item==HomeItem.APRON)cream else green,3,10,6,11);p(if(item==HomeItem.APRON)cream else green,23,10,6,11);p(gold,11,18,10,3);p(wood,14,23,5,4)}
            HomeItem.SUN_HAT->{p(wood,3,20,27,5);p(gold,8,7,16,15);p(gold,2,19,28,4);p(wood,8,17,16,3);p(cream,11,9,3,7)}
            HomeItem.WOOD->{box(wood,4,12,24,13);p(gold,6,14,5,9);p(gold,24,14,2,9);p(cream,13,15,8,2);p(wood,7,8,20,5)}
            HomeItem.STONE->{p(ink,5,13,24,14);p(Color(0xFF8B9488),8,7,17,19);p(Color(0xFFADB6A0),6,13,22,11);p(cream,11,10,9,3)}
            HomeItem.FISH,HomeItem.GRILLED_FISH->{val c=if(item==HomeItem.FISH)blue else gold;p(ink,5,13,23,10);p(c,3,12,5,13);p(c,9,9,15,16);p(c,23,13,6,8);p(cream,12,12,9,3);p(ink,24,14,2,2);if(item==HomeItem.GRILLED_FISH){p(wood,14,17,2,7);p(wood,19,17,2,7)}}
            HomeItem.BERRY_JAM->{box(red,8,9,17,20);p(wood,7,4,19,7);p(cream,10,15,13,8);p(red,14,17,5,4)}
            HomeItem.HARBOR_BADGE,HomeItem.SEAGULL_PIN->{box(gold,7,7,19,20);p(cream,11,12,12,3);p(blue,14,9,4,14);p(cream,9,15,5,3);p(cream,20,15,5,3)}
        }
    }
    fun DrawScope.symbol(symbol:HomeSymbol) {
        when(symbol) {
            HomeSymbol.BAG->{box(wood,9,3,15,9);box(gold,5,10,23,20);box(wood,9,20,15,8);p(cream,15,14,4,8);p(wood,3,14,3,13);p(wood,28,14,3,13)}
            HomeSymbol.TALK->{box(cream,3,5,26,19);p(ink,7,23,5,5);p(cream,9,21,5,4);repeat(3){p(wood,8+it*6,13,3,3)}}
            HomeSymbol.GIFT->{box(red,5,12,23,17);p(gold,14,12,5,17);box(gold,3,9,27,6);p(red,9,3,6,7);p(red,18,3,6,7);p(gold,13,6,7,5)}
            HomeSymbol.FOLLOW,HomeSymbol.WALK->{p(ink,5,6,7,15);p(wood,4,18,10,8);p(gold,6,7,5,14);p(ink,20,11,7,15);p(wood,18,24,11,5);p(gold,21,12,5,14)}
            HomeSymbol.WATER->{box(blue,7,11,16,15);box(blue,13,5,12,8);p(blue,4,13,5,8);p(blue,23,18,7,3);p(blue,28,21,3,4);p(cream,10,13,4,8);p(blue,1,25,3,4)}
            HomeSymbol.HARVEST->{p(wood,6,10,20,3);p(wood,5,13,3,14);p(wood,24,13,3,14);box(gold,3,19,27,12);p(green,10,13,4,6);p(red,15,13,8,7);p(cream,7,23,17,2)}
            HomeSymbol.AXE,HomeSymbol.CRAFT->{p(wood,13,9,5,20);p(ink,9,4,19,9);p(blue,11,5,15,6);p(cream,12,5,9,2);if(symbol==HomeSymbol.CRAFT){p(gold,3,20,6,9);p(wood,5,13,2,9)}}
            HomeSymbol.FISH->{p(wood,7,4,3,24);p(gold,7,3,20,2);p(ink,25,5,1,17);p(red,22,17,7,4);p(blue,20,23,10,2)}
            HomeSymbol.DOOR->{box(wood,7,3,20,28);p(gold,11,7,13,23);p(cream,21,19,3,3);p(blue,13,9,7,7)}
            HomeSymbol.MAIL->{box(blue,3,7,26,18);p(cream,6,11,20,10);p(red,21,4,4,12);p(wood,14,25,5,6)}
            HomeSymbol.WARDROBE->{box(gold,5,3,23,27);p(wood,15,5,2,23);p(cream,11,16,2,4);p(cream,20,16,2,4)}
            HomeSymbol.DECORATE->{p(wood,5,24,4,6);p(wood,23,24,4,6);box(green,5,8,22,17);p(green,2,16,6,11);p(green,24,16,6,11);p(cream,9,13,6,6)}
            HomeSymbol.COIN,HomeSymbol.SELL->{box(gold,6,5,21,23);p(gold,4,9,25,15);p(wood,14,9,4,13);p(cream,9,9,3,7)}
            HomeSymbol.ENERGY->{p(gold,15,3,9,5);p(gold,11,8,10,5);p(gold,7,13,17,5);p(gold,15,18,6,5);p(gold,11,23,6,6)}
            HomeSymbol.HEART->{p(red,5,7,10,14);p(red,18,7,10,14);p(red,8,14,17,10);p(red,12,24,9,3);p(red,15,27,3,3);p(cream,8,10,4,3)}
            HomeSymbol.TEA->{box(cream,5,12,19,14);box(gold,22,14,8,9);p(wood,7,12,15,3);p(blue,9,3,2,6);p(blue,17,5,2,5)}
            HomeSymbol.READ->{box(red,3,7,26,20);p(cream,6,9,9,15);p(cream,18,9,8,15);p(wood,15,7,3,20);p(gold,8,13,5,2);p(gold,20,13,4,2)}
            HomeSymbol.CLEAN->{p(wood,15,3,4,19);p(gold,9,20,17,9);p(wood,12,22,2,7);p(wood,18,22,2,7);p(cream,25,5,3,8);p(cream,22,8,9,2)}
            HomeSymbol.MEAL->{item(HomeItem.SALAD);p(wood,2,4,2,19);p(wood,28,4,2,19)}
            HomeSymbol.REST->{box(wood,3,10,26,18);p(cream,5,12,10,6);p(blue,5,19,22,6);p(wood,3,26,3,5);p(wood,26,26,3,5)}
            HomeSymbol.SEED->{item(HomeItem.CARROT_SEED)}
            HomeSymbol.TRASH->{box(blue,8,10,17,20);p(wood,5,7,23,4);p(wood,12,3,9,4);p(cream,12,14,2,11);p(cream,19,14,2,11)}
            HomeSymbol.CHECK->{p(green,4,15,5,5);p(green,8,19,6,6);p(green,13,14,5,8);p(green,18,9,5,9);p(green,23,4,5,9)}
        }
    }
}
