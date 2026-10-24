package com.ai.assistance.operit.ui.main

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.HomePet
import com.ai.assistance.operit.data.model.HomePetRules
import kotlin.math.max

internal object CompanionSceneColors {
    val wallTop=Color(0xFF384246)
    val wallBottom=Color(0xFF202628)
    val floorTop=Color(0xFF2C2925)
    val floorBottom=Color(0xFF1B1A18)
    val skyTop=Color(0xFF5C7988)
    val skyBottom=Color(0xFFC3D2CF)
    val frame=Color(0xFF252B2E)
    val cushion=Color(0xFF7E8F80)
    val cat=Color(0xFFF3E7D6)
    val catShade=Color(0xFFE6D8C5)
    val gold=Color(0xFFE9B36A)
    val rose=Color(0xFFC98E86)
    val ink=Color(0xFF262B2E)
    val hair=Color(0xFF2E2724)
    val skin=Color(0xFFE6C7AD)
    val body=Color(0xFF96827A)
    val panel=Color(0xFF14191A)
    val panelRaised=Color(0xFF1F2526)
    val panelLine=Color(0xFF2E3535)
    val moonlight=Color(0xFFF3EEE4)
    val muted=Color(0xFFA9A39B)
    val sage=Color(0xFF7E8F80)
}

@Composable
internal fun RainWindowScene(pet:HomePet?,roleName:String?,now:Long,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val motion=remember(context){Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}
    val transition=rememberInfiniteTransition(label="rain-window")
    val rain by transition.animateFloat(0f,1f,infiniteRepeatable(tween(2600,easing=LinearEasing),RepeatMode.Restart),label="rain")
    val breath by transition.animateFloat(0f,1f,infiniteRepeatable(tween(5200,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="breath")
    val glow by transition.animateFloat(0f,1f,infiniteRepeatable(tween(6000,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="glow")
    val petPreview=pet?.let {HomePetRules.preview(it,now)}
    Canvas(modifier) {
        val w=size.width;val h=size.height
        drawRect(Brush.verticalGradient(listOf(CompanionSceneColors.wallTop,CompanionSceneColors.wallBottom),0f,h))
        val scale=max(w/390f,h/700f)
        val ox=(w-390f*scale)/2f
        val oy=h*.60f-470f*scale
        fun x(v:Float)=ox+v*scale
        fun y(v:Float)=oy+v*scale
        fun s(v:Float)=v*scale
        drawRect(Brush.verticalGradient(listOf(CompanionSceneColors.floorTop,CompanionSceneColors.floorBottom),y(558f),y(844f)),
            topLeft=Offset(0f,y(558f)),size=Size(w,max(0f,h-y(558f))))
        drawWindow(x(34f),y(96f),s(322f),s(352f),s(8f),scale,if(motion)rain else .32f)
        drawCurtains(x(18f),y(80f),s(58f),s(378f))
        drawCurtainsRight(x(314f),y(80f),s(58f),s(378f))
        drawLine(Color(0xFF4A4038).copy(alpha=.45f),Offset(0f,y(558f)),Offset(w,y(558f)),s(3f))
        drawOval(Color(0xFF2F302B),Offset(x(14f),y(648f)),Size(s(364f),s(132f)))
        drawOval(Color(0xFF47473D).copy(alpha=.55f),Offset(x(46f),y(664f)),Size(s(300f),s(100f)),style=Stroke(s(1.5f)))
        drawCircle(Brush.radialGradient(listOf(CompanionSceneColors.gold.copy(alpha=.20f),Color.Transparent),
            center=Offset(x(86f),y(560f)),radius=s(160f)),radius=s(160f),center=Offset(x(86f),y(560f)))
        drawBench(x(70f),y(462f),s(272f),s(54f),s(27f))
        lamp(x(86f),y(510f),s(94f),s(1f),if(motion)glow else .4f)
        table(x(132f),y(648f),s(140f),s(10f),s(1f))
        plant(x(36f),y(748f),s(34f),s(34f))
        bowl(x(100f),y(598f),s(40f),s(16f),petPreview?.hunger?.let {it<=35}==true)
        if(pet!=null)cat(x(172f),y(486f),s(1f),if(motion)breath else .35f,petPreview?.mood?.let {it<=35}==true)
        if(roleName!=null)character(x(334f),y(566f),s(1f))
        drawMotes(x(110f),y(300f),s(1f),if(motion)glow else .3f)
    }
}

private fun DrawScope.drawWindow(left:Float,top:Float,width:Float,height:Float,radius:Float,scale:Float,rain:Float) {
    val right=left+width;val bottom=top+height
    drawRoundRect(Brush.verticalGradient(listOf(CompanionSceneColors.skyTop,CompanionSceneColors.skyBottom),top,bottom),
        Offset(left,top),Size(width,height),CornerRadius(radius))
    clipRect(left,top,right,bottom) {
        val buildings=Color(0xFF4D626B).copy(alpha=.55f)
        val bw=width/6f
        drawRect(buildings,Offset(left,top+height*.64f),Size(bw*.9f,height*.36f))
        drawRect(buildings,Offset(left+bw*1.1f,top+height*.70f),Size(bw*.7f,height*.30f))
        drawRect(buildings,Offset(left+bw*2.0f,top+height*.60f),Size(bw*.9f,height*.40f))
        drawRect(buildings,Offset(left+bw*3.0f,top+height*.73f),Size(bw*.6f,height*.27f))
        drawRect(buildings,Offset(left+bw*3.8f,top+height*.66f),Size(bw*1.1f,height*.34f))
        drawRect(buildings,Offset(left+bw*5.0f,top+height*.71f),Size(bw*.8f,height*.29f))
        val lit=CompanionSceneColors.gold.copy(alpha=.48f)
        for(i in 0..7) {
            val bx=left+bw*(i%5)+bw*.18f
            val by=top+height*(.72f+(i%3)*.06f)
            drawRect(lit,Offset(bx,by),Size(scale*4f,scale*5f))
        }
        for(i in 0 until 15) {
            val px=left+width*((i*37%100)/100f)
            val phase=(rain+i*.137f)%1f
            val py=top+phase*height*.94f
            drawLine(Color(0xFFE4EEEE).copy(alpha=.26f),Offset(px,py),Offset(px-scale*4f,py+scale*22f),scale)
        }
        drawCircle(CompanionSceneColors.gold.copy(alpha=.16f),radius=width*.42f,center=Offset(right-width*.18f,top+height*.34f))
    }
    drawRoundRect(CompanionSceneColors.frame,Offset(left,top),Size(width,height),CornerRadius(radius),style=Stroke(scale*11f))
    drawLine(CompanionSceneColors.frame,Offset(left+width*.47f,top),Offset(left+width*.47f,bottom),scale*9f)
    drawLine(CompanionSceneColors.frame,Offset(left,top+height*.47f),Offset(right,top+height*.47f),scale*9f)
    drawRoundRect(Color(0xFF4B4038),Offset(left-scale*10f,bottom-scale*4f),Size(width+scale*20f,scale*15f),CornerRadius(scale*6f))
    drawRoundRect(Color(0xFF2A2622).copy(alpha=.7f),Offset(left-scale*10f,bottom+scale*7f),Size(width+scale*20f,scale*8f),CornerRadius(scale*4f))
    drawLine(Color(0xFFDCE8E8).copy(alpha=.28f),Offset(right-width*.20f,top),Offset(right-width*.20f,top+scale*44f),scale*1.2f)
    drawPath(starPath(right-width*.20f,top+scale*50f,scale*7f,scale*3f),CompanionSceneColors.gold.copy(alpha=.85f))
}

private fun DrawScope.drawCurtains(left:Float,top:Float,width:Float,height:Float) {
    val path=Path().apply {
        moveTo(left,top);cubicTo(left+width*.7f,top+height*.18f,left+width*.2f,top+height*.70f,left+width,top+height)
        lineTo(left,top+height);close()
    }
    drawPath(path,Color(0xFF394448).copy(alpha=.92f))
    drawPath(Path().apply {moveTo(left+width*.2f,top+height*.02f);cubicTo(left+width*.7f,top+height*.2f,left+width*.35f,top+height*.7f,left+width*.9f,top+height*.98f)},
        Color(0xFF4B585C).copy(alpha=.4f),style=Stroke(width*.04f))
}

private fun DrawScope.drawCurtainsRight(left:Float,top:Float,width:Float,height:Float) {
    val path=Path().apply {
        moveTo(left+width,top);cubicTo(left+width*.3f,top+height*.18f,left+width*.8f,top+height*.70f,left,top+height)
        lineTo(left+width,top+height);close()
    }
    drawPath(path,Color(0xFF394448).copy(alpha=.92f))
}

private fun DrawScope.drawBench(left:Float,top:Float,width:Float,height:Float,radius:Float) {
    drawRoundRect(CompanionSceneColors.cushion,Offset(left,top),Size(width,height),CornerRadius(radius))
    drawRoundRect(Color(0xFF2B2A25),Offset(left+width*.04f,top+height*.85f),Size(width*.92f,height*.3f),CornerRadius(radius*.3f))
    drawLine(Color(0xFF3B342D),Offset(left+width*.08f,top+height*1.1f),Offset(left+width*.08f,top+height*1.95f),width*.033f,StrokeCap.Round)
    drawLine(Color(0xFF3B342D),Offset(left+width*.92f,top+height*1.1f),Offset(left+width*.92f,top+height*1.95f),width*.033f,StrokeCap.Round)
}

private fun DrawScope.cat(cx:Float,cy:Float,unit:Float,breath:Float,sad:Boolean) {
    drawOval(Color(0xFF131717).copy(alpha=.35f),Offset(cx-56f*unit,cy-4f*unit),Size(112f*unit,20f*unit))
    withTransform({scale(1f,1f+breath*.012f,pivot=Offset(cx,cy))}) {
        drawPath(Path().apply {
            moveTo(cx-56f*unit,cy-16f*unit)
            cubicTo(cx-60f*unit,cy-42f*unit,cx-32f*unit,cy-56f*unit,cx,cy-48f*unit)
            cubicTo(cx+24f*unit,cy-58f*unit,cx+54f*unit,cy-42f*unit,cx+52f*unit,cy-18f*unit)
            cubicTo(cx+51f*unit,cy-4f*unit,cx+32f*unit,cy+3f*unit,cx+0f*unit,cy+0f*unit)
            cubicTo(cx-28f*unit,cy-2f*unit,cx-52f*unit,cy-3f*unit,cx-56f*unit,cy-16f*unit)
            close()
        },CompanionSceneColors.cat)
        drawPath(Path().apply {
            moveTo(cx+50f*unit,cy-16f*unit)
            cubicTo(cx+70f*unit,cy-14f*unit,cx+70f*unit,cy+4f*unit,cx+50f*unit,cy+4f*unit)
            cubicTo(cx+38f*unit,cy+4f*unit,cx+34f*unit,cy-2f*unit,cx+40f*unit,cy-6f*unit)
        },CompanionSceneColors.catShade,style=Stroke(11f*unit,cap=StrokeCap.Round))
        drawCircle(CompanionSceneColors.cat,radius=25f*unit,center=Offset(cx+20f*unit,cy-30f*unit))
        drawPath(Path().apply {moveTo(cx,cy-46f*unit);lineTo(cx-6f*unit,cy-70f*unit);lineTo(cx+15f*unit,cy-56f*unit);close()},CompanionSceneColors.cat)
        drawPath(Path().apply {moveTo(cx+36f*unit,cy-50f*unit);lineTo(cx+50f*unit,cy-70f*unit);lineTo(cx+56f*unit,cy-44f*unit);close()},CompanionSceneColors.cat)
        drawPath(Path().apply {moveTo(cx+2f*unit,cy-50f*unit);lineTo(cx-2f*unit,cy-62f*unit);lineTo(cx+9f*unit,cy-54f*unit);close()},CompanionSceneColors.rose.copy(alpha=.7f))
        drawPath(Path().apply {moveTo(cx+40f*unit,cy-52f*unit);lineTo(cx+46f*unit,cy-64f*unit);lineTo(cx+50f*unit,cy-50f*unit);close()},CompanionSceneColors.rose.copy(alpha=.7f))
        val eyeY=cy-30f*unit
        drawArc(Color(0xFF6B5A4A).copy(alpha=.8f),200f,140f,false,Offset(cx+10f*unit,eyeY-4f*unit),Size(9f*unit,7f*unit),style=Stroke(1.5f*unit,cap=StrokeCap.Round))
        drawArc(Color(0xFF6B5A4A).copy(alpha=.8f),200f,140f,false,Offset(cx+26f*unit,eyeY-4f*unit),Size(9f*unit,7f*unit),style=Stroke(1.5f*unit,cap=StrokeCap.Round))
        drawPath(Path().apply {moveTo(cx+19f*unit,eyeY+2f*unit);lineTo(cx+21f*unit,eyeY+4f*unit);lineTo(cx+23f*unit,eyeY+2f*unit);close()},CompanionSceneColors.rose)
        drawLine(CompanionSceneColors.rose.copy(alpha=.75f),Offset(cx+18f*unit,eyeY+8f*unit),Offset(cx+24f*unit,eyeY+8f*unit),1.2f*unit,StrokeCap.Round)
        drawPath(starPath(cx+20f*unit,cy-51f*unit,5.5f*unit,2.4f*unit),CompanionSceneColors.gold.copy(alpha=.9f))
        drawPath(Path().apply {if(sad){moveTo(cx-48f*unit,cy+2f*unit);cubicTo(cx-70f*unit,cy+8f*unit,cx-66f*unit,cy+22f*unit,cx-54f*unit,cy+18f*unit)}else{moveTo(cx-48f*unit,cy+2f*unit);cubicTo(cx-68f*unit,cy-2f*unit,cx-68f*unit,cy+15f*unit,cx-52f*unit,cy+14f*unit)}},
            CompanionSceneColors.catShade,style=Stroke(8f*unit,cap=StrokeCap.Round))
        drawLine(CompanionSceneColors.rose.copy(alpha=.7f),Offset(cx+8f*unit,cy+4f*unit),Offset(cx+33f*unit,cy+2f*unit),2f*unit,StrokeCap.Round)
        drawPath(starPath(cx+20f*unit,cy+8f*unit,4f*unit,1.8f*unit),CompanionSceneColors.gold)
    }
}

private fun DrawScope.character(cx:Float,cy:Float,unit:Float) {
    drawOval(Color(0xFF121515).copy(alpha=.28f),Offset(cx-44f*unit,cy-8f*unit),Size(88f*unit,18f*unit))
    drawLine(Color(0xFF3C4146),Offset(cx-12f*unit,cy-68f*unit),Offset(cx-16f*unit,cy-8f*unit),17f*unit,StrokeCap.Round)
    drawLine(Color(0xFF3C4146),Offset(cx+10f*unit,cy-68f*unit),Offset(cx+14f*unit,cy-8f*unit),17f*unit,StrokeCap.Round)
    drawLine(Color(0xFF252A2D),Offset(cx-22f*unit,cy-6f*unit),Offset(cx-8f*unit,cy-6f*unit),7f*unit,StrokeCap.Round)
    drawLine(Color(0xFF252A2D),Offset(cx+8f*unit,cy-6f*unit),Offset(cx+22f*unit,cy-6f*unit),7f*unit,StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(cx-26f*unit,cy-122f*unit)
        cubicTo(cx-26f*unit,cy-140f*unit,cx+26f*unit,cy-140f*unit,cx+30f*unit,cy-116f*unit)
        lineTo(cx+34f*unit,cy-58f*unit);lineTo(cx-32f*unit,cy-58f*unit);close()
    },CompanionSceneColors.body)
    drawLine(CompanionSceneColors.moonlight.copy(alpha=.65f),Offset(cx-16f*unit,cy-122f*unit),Offset(cx+16f*unit,cy-120f*unit),5f*unit,StrokeCap.Round)
    drawPath(Path().apply {moveTo(cx-34f*unit,cy-98f*unit);lineTo(cx+4f*unit,cy-104f*unit);lineTo(cx+12f*unit,cy-82f*unit);lineTo(cx-26f*unit,cy-76f*unit);close()},Color(0xFFD8C7A8))
    drawLine(Color(0xFFB8A486),Offset(cx+4f*unit,cy-104f*unit),Offset(cx+12f*unit,cy-82f*unit),1.2f*unit)
    drawLine(CompanionSceneColors.body,Offset(cx-18f*unit,cy-114f*unit),Offset(cx-28f*unit,cy-96f*unit),13f*unit,StrokeCap.Round)
    drawLine(CompanionSceneColors.body,Offset(cx+24f*unit,cy-114f*unit),Offset(cx+22f*unit,cy-92f*unit),13f*unit,StrokeCap.Round)
    drawCircle(CompanionSceneColors.skin,23f*unit,center=Offset(cx+2f*unit,cy-156f*unit))
    drawPath(Path().apply {
        moveTo(cx-18f*unit,cy-168f*unit)
        cubicTo(cx-10f*unit,cy-186f*unit,cx+22f*unit,cy-186f*unit,cx+26f*unit,cy-158f*unit)
        cubicTo(cx+28f*unit,cy-142f*unit,cx+18f*unit,cy-132f*unit,cx+4f*unit,cy-132f*unit)
        cubicTo(cx-4f*unit,cy-132f*unit,cx-12f*unit,cy-136f*unit,cx-16f*unit,cy-144f*unit)
        cubicTo(cx-20f*unit,cy-150f*unit,cx-20f*unit,cy-160f*unit,cx-18f*unit,cy-168f*unit)
        close()
    },CompanionSceneColors.hair)
    drawCircle(CompanionSceneColors.hair,9f*unit,center=Offset(cx+23f*unit,cy-176f*unit))
    drawLine(Color(0xFFCFA98C),Offset(cx-19f*unit,cy-154f*unit),Offset(cx-21f*unit,cy-149f*unit),2f*unit,StrokeCap.Round)
    drawArc(Color(0xFF6B5A4A),195f,150f,false,Offset(cx-15f*unit,cy-160f*unit),Size(7f*unit,6f*unit),style=Stroke(1.2f*unit,cap=StrokeCap.Round))
    drawPath(Path().apply {moveTo(cx-20f*unit,cy-168f*unit);cubicTo(cx-24f*unit,cy-152f*unit,cx-22f*unit,cy-138f*unit,cx-16f*unit,cy-128f*unit)},
        CompanionSceneColors.gold.copy(alpha=.32f),style=Stroke(1.5f*unit,cap=StrokeCap.Round))
}

private fun DrawScope.lamp(cx:Float,baseY:Float,radius:Float,unit:Float,glow:Float) {
    drawCircle(Brush.radialGradient(listOf(CompanionSceneColors.gold.copy(alpha=.18f+.24f*glow),Color.Transparent),
        center=Offset(cx,baseY+10f*unit),radius=radius),radius=radius,center=Offset(cx,baseY+10f*unit))
    drawLine(Color(0xFF2B2824),Offset(cx,baseY+18f*unit),Offset(cx,baseY+136f*unit),4f*unit,StrokeCap.Round)
    drawOval(Color(0xFF24211E),Offset(cx-26f*unit,baseY+134f*unit),Size(52f*unit,12f*unit))
    drawPath(Path().apply {
        moveTo(cx-24f*unit,baseY+18f*unit);lineTo(cx+24f*unit,baseY+18f*unit)
        lineTo(cx+16f*unit,baseY-10f*unit);lineTo(cx-16f*unit,baseY-10f*unit);close()
    },Color(0xFFE7B877).copy(alpha=.86f+.1f*glow))
}

private fun DrawScope.table(left:Float,top:Float,width:Float,height:Float,unit:Float) {
    drawRoundRect(Color(0xFF5A4636),Offset(left,top),Size(width,height),CornerRadius(5f*unit))
    drawLine(Color(0xFF4A3A2D),Offset(left+14f*unit,top+height),Offset(left+8f*unit,top+72f*unit),6f*unit,StrokeCap.Round)
    drawLine(Color(0xFF4A3A2D),Offset(left+width-14f*unit,top+height),Offset(left+width-8f*unit,top+72f*unit),6f*unit,StrokeCap.Round)
    mug(left+30f*unit,top-20f*unit,unit,Color(0xFFE8DDC9))
    mug(left+76f*unit,top-18f*unit,unit,CompanionSceneColors.rose)
    drawPath(Path().apply {moveTo(left+106f*unit,top-4f*unit);lineTo(left+130f*unit,top-6f*unit);lineTo(left+132f*unit,top);lineTo(left+108f*unit,top+2f*unit);close()},Color(0xFFD8C7A8))
    drawLine(Color(0xFFB8A486),Offset(left+118f*unit,top-5f*unit),Offset(left+120f*unit,top+1f*unit),1f*unit)
}

private fun DrawScope.mug(left:Float,top:Float,unit:Float,color:Color) {
    drawRoundRect(color,Offset(left,top),Size(22f*unit,18f*unit),CornerRadius(3f*unit))
    drawArc(color,270f,180f,false,Offset(left+19f*unit,top+4f*unit),Size(10f*unit,10f*unit),style=Stroke(3f*unit,cap=StrokeCap.Round))
    drawOval(Color(0xFF8A6A4E),Offset(left,top),Size(22f*unit,6f*unit))
}

private fun DrawScope.plant(cx:Float,baseY:Float,potW:Float,potH:Float) {
    drawLine(Color(0xFF536B50),Offset(cx,baseY),Offset(cx-18f,baseY-56f),9f*unitScale(potW),StrokeCap.Round)
    drawLine(Color(0xFF5E7658),Offset(cx+8f,baseY),Offset(cx+28f,baseY-68f),9f*unitScale(potW),StrokeCap.Round)
    drawLine(Color(0xFF4B6348),Offset(cx+12f,baseY),Offset(cx+50f,baseY-30f),8f*unitScale(potW),StrokeCap.Round)
    drawPath(Path().apply {moveTo(cx-potW*.5f,baseY);lineTo(cx+potW*.5f,baseY);lineTo(cx+potW*.35f,baseY+potH);lineTo(cx-potW*.35f,baseY+potH);close()},Color(0xFF8A6353))
    drawLine(Color(0xFFA77A65),Offset(cx-potW*.5f,baseY),Offset(cx+potW*.5f,baseY),3f*unitScale(potW))
}

private fun unitScale(value:Float)=value/34f

private fun DrawScope.bowl(cx:Float,top:Float,width:Float,height:Float,hungry:Boolean) {
    drawOval(Color(0xFF121515).copy(alpha=.35f),Offset(cx-width/2f,top+height),Size(width,height*.4f))
    drawPath(Path().apply {moveTo(cx-width/2f,top);lineTo(cx+width/2f,top);lineTo(cx+width*.38f,top+height);lineTo(cx-width*.38f,top+height);close()},Color(0xFFCDB894))
    drawOval(Color(0xFF7C5A3E),Offset(cx-width*.46f,top-height*.2f),Size(width*.92f,height*.42f))
    drawOval(Color(0xFFB98D5E),Offset(cx-width*.32f,top-height*.14f),Size(width*.64f,height*.28f))
    if(hungry)drawCircle(CompanionSceneColors.gold.copy(alpha=.55f),width*.08f,center=Offset(cx,top-height*.45f))
}

private fun DrawScope.drawMotes(cx:Float,cy:Float,unit:Float,glow:Float) {
    val alpha=.14f+.26f*glow
    drawCircle(CompanionSceneColors.gold.copy(alpha=alpha),1.6f*unit,center=Offset(cx,cy))
    drawCircle(CompanionSceneColors.gold.copy(alpha=alpha*.8f),1.2f*unit,center=Offset(cx+100f*unit,cy+60f*unit))
    drawCircle(CompanionSceneColors.gold.copy(alpha=alpha*.7f),1.4f*unit,center=Offset(cx-40f*unit,cy+100f*unit))
    drawCircle(CompanionSceneColors.gold.copy(alpha=alpha*.6f),1f*unit,center=Offset(cx+170f*unit,cy+30f*unit))
}

private fun starPath(cx:Float,cy:Float,outer:Float,inner:Float):Path {
    val path=Path()
    for(i in 0 until 10) {
        val angle=(-90f+i*36f)*Math.PI.toFloat()/180f
        val radius=if(i%2==0)outer else inner
        val px=cx+radius*kotlin.math.cos(angle.toDouble()).toFloat()
        val py=cy+radius*kotlin.math.sin(angle.toDouble()).toFloat()
        if(i==0)path.moveTo(px,py)else path.lineTo(px,py)
    }
    path.close();return path
}

@Composable
internal fun PetAvatar(size:Dp=46.dp) {
    Canvas(Modifier.size(size)) {
        drawCircle(Color(0xFF343A39))
        val u=this.size.minDimension/48f
        fun p(v:Float)=v*u
        drawPath(Path().apply {moveTo(p(12f),p(20f));lineTo(p(10f),p(10f));lineTo(p(20f),p(14f));close()},CompanionSceneColors.cat)
        drawPath(Path().apply {moveTo(p(36f),p(20f));lineTo(p(38f),p(10f));lineTo(p(28f),p(14f));close()},CompanionSceneColors.cat)
        drawPath(Path().apply {moveTo(p(14f),p(19f));lineTo(p(13f),p(13f));lineTo(p(18f),p(15f));close()},CompanionSceneColors.rose.copy(alpha=.8f))
        drawPath(Path().apply {moveTo(p(34f),p(19f));lineTo(p(35f),p(13f));lineTo(p(30f),p(15f));close()},CompanionSceneColors.rose.copy(alpha=.8f))
        drawCircle(CompanionSceneColors.cat,p(13f),center=Offset(p(24f),p(26f)))
        drawArc(Color(0xFF6B5A4A),200f,140f,false,Offset(p(18f),p(24f)),Size(p(5f),p(4f)),style=Stroke(p(1.4f),cap=StrokeCap.Round))
        drawArc(Color(0xFF6B5A4A),200f,140f,false,Offset(p(25f),p(24f)),Size(p(5f),p(4f)),style=Stroke(p(1.4f),cap=StrokeCap.Round))
        drawPath(Path().apply {moveTo(p(23f),p(30f));lineTo(p(24f),p(31f));lineTo(p(25f),p(30f));close()},CompanionSceneColors.rose)
        drawPath(starPath(p(24f),p(18f),p(2.4f),p(1.1f)),CompanionSceneColors.gold)
    }
}

@Composable
internal fun CompanionAvatar(label:String,color:Color,size:Dp=26.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color),contentAlignment=Alignment.Center) {
        Text(label.take(1).ifEmpty { "伴" },color=Color.White,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Clip)
    }
}
