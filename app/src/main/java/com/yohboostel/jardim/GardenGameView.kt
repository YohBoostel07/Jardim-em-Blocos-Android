package com.yohboostel.jardim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GardenGameView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("garden_game", Context.MODE_PRIVATE)
    private val board = Array(9) { BooleanArray(9) }
    private val colors = Array(9) { IntArray(9) }
    private val palette = intArrayOf(
        Color.rgb(83, 190, 139), Color.rgb(93, 164, 226),
        Color.rgb(246, 183, 91), Color.rgb(204, 137, 211),
        Color.rgb(239, 119, 126), Color.rgb(103, 199, 198)
    )
    private val shapes = listOf(
        listOf(0 to 0),
        listOf(0 to 0, 1 to 0), listOf(0 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0), listOf(0 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 0 to 1), listOf(0 to 0, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 1), listOf(1 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
        listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
        listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2),
        listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2),
        listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1, 1 to 2)
    )

    private data class Piece(val cells: List<Pair<Int, Int>>, val color: Int)
    private data class Spark(val x: Float, val y: Float, val color: Int, val size: Float, val vx: Float = 0f, val vy: Float = 0f, val rotation: Float = 0f)
    private data class Blast(val x: Float, val y: Float, val color: Int)
    private var blastCenters = emptyList<Blast>()

    private var pieces = MutableList<Piece?>(3) { newPiece() }
    private var selected = 0
    private var score = 0
    private var best = prefs.getInt("best_score", 0)
    private var level = 1
    private var gameOver = false
    private var paused = false
    private var showTutorial = !prefs.getBoolean("tutorial_seen", false)
    private var confirmRestart = false
    private var soundEnabled = prefs.getBoolean("sound_enabled", true)
    private var gamesPlayed = prefs.getInt("games_played", 0)
    private var totalLines = prefs.getInt("total_lines", 0)
    private var bestCombo = prefs.getInt("best_combo", 0)
    private var mainMenuOpen = true
    private var showAchievements = false
    private var showExpeditions = false
    private var expeditionUnlocked = prefs.getInt("expedition_unlocked", 0).coerceIn(0, 5)
    private var expeditionStage = 0
    private var activeExpedition = false
    private var showExpeditionComplete = false
    private val expeditionGoals = intArrayOf(300, 600, 900, 1300, 1800)
    private val expeditionRewards = intArrayOf(100, 150, 200, 300, 500)
    private val expeditionNames = listOf("Primeiros brotos", "Canteiro em ordem", "Jardim vibrante", "Bosque renovado", "Paraíso florescido")
    private var showGardens = false
    private var gardenDetailOpen = false
    private var selectedGarden = 0
    private var showShop = false
    private var activePower = ""
    private var hammerCount = prefs.getInt("power_hammer", 0)
    private var rowBombCount = prefs.getInt("power_row_bomb", 0)
    private var rerollCount = prefs.getInt("power_reroll", 0)
    private var coins = prefs.getInt("garden_coins", 100)
    private val gardenNames = listOf("Jardim das Flores", "Bosque Encantado", "Lago Sereno")
    private val gardenDescriptions = listOf("Recupere o canteiro e traga as cores de volta.", "Desperte a vida entre as árvores.", "Limpe as águas e floresça as margens.")
    private val gardenCosts = arrayOf(intArrayOf(50, 80, 120), intArrayOf(100, 150, 200), intArrayOf(150, 200, 250))
    private val gardenStages = IntArray(3) { prefs.getInt("garden_stage_$it", 0).coerceIn(0, 3) }
    private var dailyLines = prefs.getInt("daily_lines", 0)
    private var dailyRewardClaimed = prefs.getBoolean("daily_reward_claimed", false)
    private var weeklyLines = prefs.getInt("weekly_lines", 0)
    private var weeklyRewardClaimed = prefs.getBoolean("weekly_reward_claimed", false)
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private var boardSize = 0f
    private var slotsTop = 0f
    private var trayCenterY = 0f
    private var dragging = false
    private var previewRow = -1
    private var previewCol = -1
    private var previewValid = false
    private var comboLabel = ""
    private var comboAlpha = 0f
    private var sparks = emptyList<Spark>()
    private var effectProgress = 1f
    private var lastComboLines = 0
    private var lastMoveTime = 0L
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65)

    init {
        isFocusable = true
        isClickable = true
        refreshDailyMission()
        refreshWeeklyMission()
    }

    private fun newPiece(): Piece {
        // Mistura peças pequenas, médias e grandes sem tornar o sorteio injusto.
        val roll = Random.nextInt(100)
        val index = when {
            roll < 18 -> Random.nextInt(0, 6)
            roll < 72 -> Random.nextInt(1, 14)
            else -> Random.nextInt(9, shapes.size)
        }
        return Piece(shapes[index], palette[Random.nextInt(palette.size)])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(17, 55, 48), Color.rgb(29, 91, 67), Color.rgb(38, 105, 73)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        drawGardenBackdrop(canvas, w, h)
        if (mainMenuOpen) {
            when {
                showAchievements -> drawAchievementsScreen(canvas, w, h)
                showExpeditions -> drawExpeditionsScreen(canvas, w, h)
                else -> drawMainMenu(canvas, w, h)
            }
            return
        }
        boardSize = min(w * .90f, h * .455f)
        cell = boardSize / 9f
        boardLeft = (w - boardSize) / 2f
        boardTop = h * .235f
        slotsTop = boardTop + boardSize + h * .062f
        trayCenterY = slotsTop + h * .054f

        drawHeader(canvas, w, h)
        drawBoard(canvas)
        drawPieceTray(canvas, w, h)
        drawFooter(canvas, w, h)
        if (sparks.isNotEmpty()) drawSparks(canvas)
        if (showGardens) {
            if (gardenDetailOpen) drawGardenDetailScreen(canvas, w, h) else drawGardensScreen(canvas, w, h)
        }
        if (showShop) drawShopScreen(canvas, w, h)
        if (showExpeditionComplete) drawExpeditionCompleteScreen(canvas, w, h)
        if (showTutorial) drawTutorial(canvas, w, h)
        else if (confirmRestart) drawRestartConfirmation(canvas, w, h)
        else if (paused) drawPauseMenu(canvas, w, h)
        else if (gameOver) drawGameOver(canvas, w, h)
    }

    private fun drawMainMenu(c: Canvas, w: Float, h: Float) {
        drawGardenBackdrop(c,w,h)
        // Moldura superior e assinatura do jogo.
        val logo = RectF(w*.10f,h*.035f,w*.90f,h*.245f)
        drawPremiumCard(c,logo,30f,Color.argb(205,7,38,37),Color.argb(175,12,67,53))
        paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(255,229,157)
        paint.textSize=w*.105f
        c.drawText("JARDIM",w*.5f,h*.125f,paint)
        paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        paint.color=Color.WHITE
        paint.textSize=w*.067f
        c.drawText("EM BLOCOS",w*.5f,h*.195f,paint)
        paint.color=Color.rgb(180,232,192)
        paint.textSize=w*.022f
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        c.drawText("✦  CULTIVE • COMBINE • FLORESÇA  ✦",w*.5f,h*.225f,paint)

        // Grande vitrine do mundo restaurável.
        val hero=RectF(w*.075f,h*.285f,w*.925f,h*.565f)
        paint.color=Color.argb(100,0,0,0);c.drawRoundRect(RectF(hero.left,hero.top+h*.012f,hero.right,hero.bottom+h*.012f),28f,28f,paint)
        drawPremiumCard(c,hero,28f,Color.rgb(137,191,167),Color.rgb(34,103,70))
        drawGardenMiniature(c,0,gardenStages[0],RectF(hero.left+w*.025f,hero.top+h*.018f,hero.right-w*.025f,hero.bottom-h*.012f))
        paint.shader=LinearGradient(0f,hero.centerY(),0f,hero.bottom,
            intArrayOf(Color.TRANSPARENT,Color.argb(220,5,35,30)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(hero,28f,28f,paint);paint.shader=null
        paint.color=Color.WHITE;paint.textSize=w*.031f;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        c.drawText("UM MUNDO ESPERA POR VOCÊ",w*.5f,h*.525f,paint)
        paint.textSize=w*.021f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        c.drawText("Restaure jardins, conquiste terras e evolua",w*.5f,h*.551f,paint)

        val play=RectF(w*.13f,h*.625f,w*.87f,h*.725f)
        drawPremiumCard(c,play,25f,Color.rgb(255,231,159),Color.rgb(220,157,64))
        paint.color=Color.rgb(58,66,39);paint.textSize=w*.052f;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        c.drawText("▶  JOGAR",play.centerX(),play.centerY()+w*.017f,paint)

        val buttons=listOf(
            RectF(w*.055f,h*.755f,w*.345f,h*.845f),
            RectF(w*.355f,h*.755f,w*.645f,h*.845f),
            RectF(w*.655f,h*.755f,w*.945f,h*.845f))
        val labels=listOf("MEUS JARDINS","EXPEDIÇÃO","CONQUISTAS")
        val icons=listOf("✿","✧","★")
        for(i in buttons.indices){
            drawPremiumCard(c,buttons[i],18f,Color.rgb(244,237,206),Color.rgb(208,215,172))
            paint.textAlign=Paint.Align.CENTER;paint.color=Color.rgb(55,105,70)
            paint.textSize=w*.040f;paint.typeface=Typeface.create("serif",Typeface.BOLD)
            c.drawText(icons[i],buttons[i].centerX(),buttons[i].top+h*.036f,paint)
            paint.textSize=w*.021f;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            c.drawText(labels[i],buttons[i].centerX(),buttons[i].bottom-h*.018f,paint)
        }
        drawPremiumCard(c,RectF(w*.20f,h*.875f,w*.80f,h*.925f),18f,Color.argb(215,8,43,38),Color.argb(195,14,69,53))
        paint.color=Color.rgb(255,226,150);paint.textSize=w*.027f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        c.drawText("✦  $coins MOEDAS     •     RECORDE $best",w*.5f,h*.907f,paint)
        paint.color=Color.argb(185,236,249,222);paint.textSize=w*.019f
        c.drawText("NÍVEL "+(1+best/500)+"  •  SUA JORNADA CONTINUA",w*.5f,h*.955f,paint)
    }

    private fun drawAchievementsScreen(c: Canvas,w: Float,h: Float){
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.045f,h*.045f,w*.955f,h*.955f),30f,Color.rgb(247,239,207),Color.rgb(207,220,181))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(48,91,65);paint.textSize=w*.056f
        c.drawText("SALÃO DAS CONQUISTAS",w*.5f,h*.115f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.023f
        paint.color=Color.rgb(110,119,87);c.drawText("Desafios especiais • recompensas em moedas",w*.5f,h*.155f,paint)
        val titles=listOf("Primeira partida","Jardineiro dedicado","Pontuação de ouro","Combo espetacular","Mestre das linhas","Mundo florescido")
        val details=listOf("Jogue sua primeira partida","Limpe 10 linhas no total","Alcance 500 pontos","Limpe 3 linhas de uma vez","Limpe 50 linhas no total","Restaure 3 etapas de jardins")
        val unlocked=listOf(gamesPlayed>=1,totalLines>=10,best>=500,bestCombo>=3,totalLines>=50,gardenStages.sum()>=3)
        val keys=listOf("ach_first","ach_lines","ach_score","ach_combo","ach_master_lines","ach_world")
        val rewards=intArrayOf(50,75,100,100,150,200)
        for(i in 0..5){
            val top=h*(.195f+i*.102f);val rect=RectF(w*.085f,top,w*.915f,top+h*.087f)
            drawPremiumCard(c,rect,15f,if(unlocked[i])Color.rgb(255,252,232)else Color.rgb(220,219,193),if(unlocked[i])Color.rgb(231,239,207)else Color.rgb(198,204,176))
            paint.color=if(unlocked[i])Color.rgb(220,166,63)else Color.rgb(148,157,132)
            c.drawCircle(w*.16f,top+h*.043f,w*.034f,paint)
            paint.color=Color.WHITE;paint.textSize=w*.035f
            c.drawText(if(unlocked[i])"★"else"·",w*.16f,top+h*.055f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            paint.color=Color.rgb(47,91,64);paint.textSize=w*.027f;c.drawText(titles[i],w*.23f,top+h*.036f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.019f
            paint.color=Color.rgb(103,111,87);c.drawText(details[i],w*.23f,top+h*.065f,paint)
            val claimed=prefs.getBoolean(keys[i],false)
            val rr=RectF(w*.70f,top+h*.018f,w*.88f,top+h*.071f)
            drawPremiumCard(c,rr,10f,if(unlocked[i]&&!claimed)Color.rgb(105,174,105)else Color.rgb(181,193,164),if(unlocked[i]&&!claimed)Color.rgb(49,124,72)else Color.rgb(145,160,132))
            paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            paint.color=Color.WHITE;paint.textSize=w*.018f
            c.drawText(if(claimed)"RESGATADA"else if(unlocked[i])"+"+rewards[i]+" ✦"else"BLOQUEADA",rr.centerX(),rr.centerY()+w*.006f,paint)
        }
        val back=RectF(w*.22f,h*.84f,w*.78f,h*.91f)
        drawPremiumCard(c,back,17f,Color.rgb(72,151,94),Color.rgb(34,103,67))
        paint.color=Color.WHITE;paint.textSize=w*.028f;c.drawText("VOLTAR AO MENU",back.centerX(),back.centerY()+w*.01f,paint)
    }

    private fun drawExpeditionsScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.045f,h*.055f,w*.955f,h*.945f),30f,Color.rgb(247,239,207),Color.rgb(205,220,180))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64);paint.textSize=w*.056f
        c.drawText("A GRANDE EXPEDIÇÃO",w*.5f,h*.125f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.023f
        paint.color=Color.rgb(103,111,87);c.drawText("Cinco jornadas para restaurar o reino",w*.5f,h*.165f,paint)
        for(i in 0..4){
            val top=h*(.205f+i*.118f);val unlocked=i<=expeditionUnlocked;val completed=i<expeditionUnlocked
            val rect=RectF(w*.085f,top,w*.915f,top+h*.102f)
            drawPremiumCard(c,rect,16f,if(completed)Color.rgb(226,241,207)else if(unlocked)Color.rgb(255,251,228)else Color.rgb(218,218,192),if(completed)Color.rgb(190,220,165)else if(unlocked)Color.rgb(233,232,199)else Color.rgb(193,201,176))
            paint.color=if(completed)Color.rgb(80,151,88)else if(unlocked)Color.rgb(217,165,66)else Color.rgb(144,151,128)
            c.drawCircle(w*.145f,top+h*.051f,w*.031f,paint)
            paint.color=Color.WHITE;paint.textSize=w*.026f
            c.drawText(if(completed)"✓"else(i+1).toString(),w*.145f,top+h*.060f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            paint.color=if(unlocked)Color.rgb(47,91,64)else Color.rgb(130,133,116);paint.textSize=w*.027f
            c.drawText(expeditionNames[i],w*.21f,top+h*.040f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.020f;paint.color=Color.rgb(103,111,87)
            c.drawText("Meta: "+expeditionGoals[i]+" pontos",w*.21f,top+h*.073f,paint)
            paint.textAlign=Paint.Align.RIGHT;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=w*.020f
            paint.color=if(completed)Color.rgb(65,135,79)else if(unlocked)Color.rgb(174,116,43)else Color.rgb(130,133,116)
            c.drawText(if(completed)"CONCLUÍDA"else if(unlocked)"+"+expeditionRewards[i]+" ✦"else"🔒",w*.88f,top+h*.056f,paint)
        }
        val back=RectF(w*.22f,h*.825f,w*.78f,h*.90f)
        drawPremiumCard(c,back,17f,Color.rgb(72,151,94),Color.rgb(34,103,67))
        paint.textAlign=Paint.Align.CENTER;paint.color=Color.WHITE;paint.textSize=w*.029f
        c.drawText("VOLTAR AO MENU",w*.5f,h*.87f,paint)
    }

    private fun drawExpeditionCompleteScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.075f,h*.30f,w*.925f,h*.72f),30f,Color.rgb(255,239,174),Color.rgb(201,220,167))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(57,105,66);paint.textSize=w*.060f
        c.drawText("✦  ETAPA CONCLUÍDA!  ✦",w*.5f,h*.405f,paint)
        paint.textSize=w*.034f;paint.color=Color.rgb(165,111,43)
        c.drawText(expeditionNames[expeditionStage],w*.5f,h*.465f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.030f
        paint.color=Color.rgb(89,111,76)
        c.drawText("Você recebeu "+expeditionRewards[expeditionStage]+" moedas",w*.5f,h*.515f,paint)
        c.drawText(if(expeditionUnlocked>=5)"O reino está totalmente restaurado!"else"Próxima jornada: "+(expeditionUnlocked+1)+" de 5",w*.5f,h*.56f,paint)
        drawButton(c,w,h*.645f,"CONTINUAR",true)
    }

    private fun drawGardenBackdrop(c: Canvas, w: Float, h: Float) {
        // Céu de fantasia com luz volumétrica.
        paint.shader=LinearGradient(0f,0f,0f,h,intArrayOf(Color.rgb(87,190,225),Color.rgb(155,218,224),Color.rgb(232,220,166)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint);paint.shader=null
        paint.shader=RadialGradient(w*.79f,h*.15f,w*.24f,intArrayOf(Color.rgb(255,255,219),Color.argb(95,255,235,157),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.drawCircle(w*.79f,h*.15f,w*.24f,paint);paint.shader=null
        paint.color=Color.rgb(255,246,197);c.drawCircle(w*.79f,h*.15f,w*.052f,paint)
        // Nuvens fofas em camadas.
        for(i in 0..3){val x=w*(.08f+i*.29f);val y=h*(.12f+(i%2)*.075f);drawCloud3D(c,x,y,w*.13f,h*.035f)}
        // Montanhas distantes com faces iluminadas.
        val m1=android.graphics.Path().apply{moveTo(0f,h*.49f);lineTo(w*.18f,h*.25f);lineTo(w*.32f,h*.47f);lineTo(w*.49f,h*.20f);lineTo(w*.72f,h*.49f);lineTo(w*.88f,h*.30f);lineTo(w,h*.48f);lineTo(w,h);lineTo(0f,h);close()}
        paint.color=Color.rgb(91,155,148);c.drawPath(m1,paint)
        val snow=android.graphics.Path().apply{moveTo(w*.18f,h*.25f);lineTo(w*.135f,h*.315f);lineTo(w*.19f,h*.295f);lineTo(w*.235f,h*.34f);close();moveTo(w*.49f,h*.20f);lineTo(w*.445f,h*.285f);lineTo(w*.50f,h*.265f);lineTo(w*.55f,h*.31f);close()}
        paint.color=Color.rgb(230,239,213);c.drawPath(snow,paint)
        val hillBack=android.graphics.Path().apply{moveTo(0f,h*.57f);cubicTo(w*.25f,h*.38f,w*.42f,h*.62f,w*.63f,h*.47f);cubicTo(w*.82f,h*.35f,w*.92f,h*.52f,w,h*.43f);lineTo(w,h);lineTo(0f,h);close()}
        paint.color=Color.rgb(83,166,105);c.drawPath(hillBack,paint)
        val hillFront=android.graphics.Path().apply{moveTo(0f,h*.70f);cubicTo(w*.22f,h*.53f,w*.40f,h*.72f,w*.62f,h*.59f);cubicTo(w*.79f,h*.48f,w*.92f,h*.68f,w,h*.55f);lineTo(w,h);lineTo(0f,h);close()}
        paint.shader=LinearGradient(0f,h*.53f,0f,h,intArrayOf(Color.rgb(104,190,105),Color.rgb(37,121,72),Color.rgb(19,83,57)),null,Shader.TileMode.CLAMP)
        c.drawPath(hillFront,paint);paint.shader=null
        // Trilha sinuosa com bordas em relevo.
        val path=android.graphics.Path().apply{moveTo(w*.02f,h*.88f);cubicTo(w*.27f,h*.67f,w*.43f,h*.89f,w*.58f,h*.73f);cubicTo(w*.72f,h*.60f,w*.82f,h*.72f,w,h*.60f)}
        paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=w*.105f;paint.color=Color.rgb(110,83,54);c.drawPath(path,paint)
        paint.strokeWidth=w*.080f;paint.color=Color.rgb(221,184,116);c.drawPath(path,paint);paint.strokeWidth=w*.006f;paint.color=Color.argb(90,255,243,193);c.drawPath(path,paint);paint.style=Paint.Style.FILL;paint.strokeCap=Paint.Cap.BUTT
        // Árvores em primeiro plano, cada uma com tronco facetado e copas esféricas.
        for(i in 0..6){val x=w*(.035f+i*.155f);val y=h*(.69f+(i%3)*.075f);drawTree3D(c,x,y,w*(.10f+(i%2)*.018f),if(i%2==0)Color.rgb(55,151,75)else Color.rgb(43,129,73),.78f+(i%3)*.12f)}
        // Arbustos, pedras e flores individuais.
        for(i in 0..9){val x=w*((i*29%93)/100f);val y=h*(.69f+(i*13%24)/100f);drawBush3D(c,x,y,w*.035f);if(i%2==0)drawRock3D(c,x+w*.018f,y+h*.008f,w*.025f,h*.014f)}
        for(i in 0..22){val x=w*((i*41%97)/100f);val y=h*(.60f+(i*17%34)/100f);drawFlower3D(c,x,y,w*.014f,when(i%4){0->Color.rgb(255,103,145);1->Color.rgb(255,220,93);2->Color.rgb(190,143,239);else->Color.WHITE})}
        for(i in 0..15){val x=w*((i*31%95)/100f);val y=h*(.23f+(i*19%65)/100f);paint.color=Color.argb(155,255,243,163);c.drawCircle(x,y,w*.003f,paint);paint.color=Color.argb(55,255,255,207);c.drawCircle(x,y,w*.009f,paint)}
    }

    private fun drawCloud3D(c:Canvas,x:Float,y:Float,rx:Float,ry:Float){
        paint.color=Color.argb(45,43,91,105);c.drawOval(x-rx,y-ry*.05f,x+rx,y+ry*1.35f,paint)
        paint.shader=LinearGradient(x,y-ry,x,y+ry,Color.WHITE,Color.rgb(207,235,231),Shader.TileMode.CLAMP)
        c.drawOval(x-rx,y-ry,x+rx,y+ry,paint);paint.shader=null
        paint.color=Color.argb(125,255,255,255);c.drawCircle(x-rx*.35f,y-ry*.25f,ry*.65f,paint);c.drawCircle(x,y-ry*.40f,ry*.78f,paint)
    }

    private fun drawSphere3D(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,base:Int){
        val r=Color.red(base);val g=Color.green(base);val b=Color.blue(base)
        paint.shader=RadialGradient(x-rx*.32f,y-ry*.42f,max(rx,ry)*1.45f,intArrayOf(Color.rgb(min(255,r+65),min(255,g+65),min(255,b+65)),base,Color.rgb(max(0,r-55),max(0,g-55),max(0,b-45))),floatArrayOf(0f,.58f,1f),Shader.TileMode.CLAMP)
        c.drawOval(x-rx,y-ry,x+rx,y+ry,paint);paint.shader=null
        paint.color=Color.argb(75,255,255,255);c.drawOval(x-rx*.58f,y-ry*.60f,x+rx*.08f,y-ry*.18f,paint)
    }

    private fun drawTree3D(c:Canvas,x:Float,baseY:Float,size:Float,leafColor:Int,scale:Float=1f){
        val hh=size*1.55f*scale;val trunk=size*.24f*scale
        paint.color=Color.argb(65,15,45,27);c.drawOval(x-size*.72f,baseY-size*.10f,x+size*.72f,baseY+size*.18f,paint)
        val trunkPath=android.graphics.Path().apply{moveTo(x-trunk*.35f,baseY);lineTo(x-trunk*.22f,baseY-hh*.62f);lineTo(x+trunk*.20f,baseY-hh*.62f);lineTo(x+trunk*.48f,baseY);close()}
        paint.shader=LinearGradient(x-trunk,baseY-hh*.5f,x+trunk,baseY,Color.rgb(151,94,48),Color.rgb(75,48,35),Shader.TileMode.CLAMP);c.drawPath(trunkPath,paint);paint.shader=null
        paint.color=Color.rgb(185,126,68);c.drawLine(x-trunk*.05f,baseY-hh*.16f,x-trunk*.12f,baseY-hh*.55f,paint)
        drawSphere3D(c,x,baseY-hh*.82f,size*.76f,size*.70f,leafColor)
        drawSphere3D(c,x-size*.48f,baseY-hh*.66f,size*.53f,size*.49f,leafColor)
        drawSphere3D(c,x+size*.48f,baseY-hh*.66f,size*.53f,size*.49f,leafColor)
        drawSphere3D(c,x-size*.10f,baseY-hh*1.10f,size*.48f,size*.44f,leafColor)
    }

    private fun drawBush3D(c:Canvas,x:Float,y:Float,size:Float){
        paint.color=Color.argb(55,20,53,29);c.drawOval(x-size,y-size*.12f,x+size,y+size*.25f,paint)
        drawSphere3D(c,x-size*.38f,y-size*.22f,size*.55f,size*.47f,Color.rgb(47,142,71))
        drawSphere3D(c,x+size*.28f,y-size*.30f,size*.62f,size*.52f,Color.rgb(67,166,77))
        drawSphere3D(c,x,y-size*.52f,size*.56f,size*.50f,Color.rgb(79,180,82))
    }

    private fun drawRock3D(c:Canvas,x:Float,y:Float,rx:Float,ry:Float){
        val p=android.graphics.Path().apply{moveTo(x-rx,y);lineTo(x-rx*.65f,y-ry*.72f);lineTo(x+rx*.15f,y-ry);lineTo(x+rx*.82f,y-ry*.55f);lineTo(x+rx,y);lineTo(x+rx*.5f,y+ry*.24f);lineTo(x-rx*.65f,y+ry*.18f);close()}
        paint.color=Color.argb(65,25,50,40);c.drawOval(x-rx,y-ry*.12f,x+rx,y+ry*.42f,paint)
        paint.shader=LinearGradient(x-rx,y-ry,x+rx,y+ry,Color.rgb(205,211,187),Color.rgb(91,111,100),Shader.TileMode.CLAMP);c.drawPath(p,paint);paint.shader=null
        paint.color=Color.argb(100,255,255,240);c.drawLine(x-rx*.45f,y-ry*.48f,x+rx*.10f,y-ry*.78f,paint)
    }

    private fun drawFlower3D(c:Canvas,x:Float,y:Float,size:Float,petalColor:Int){
        paint.color=Color.argb(55,20,53,25);c.drawOval(x-size*.7f,y-size*.08f,x+size*.7f,y+size*.30f,paint)
        paint.color=Color.rgb(47,126,57);paint.strokeWidth=max(1.5f,size*.12f);c.drawLine(x,y,x-size*.10f,y-size*1.35f,paint);paint.strokeWidth=1f
        paint.color=Color.rgb(83,181,79);c.drawOval(x-size*.85f,y-size*.78f,x-size*.12f,y-size*.35f,paint);c.drawOval(x+size*.12f,y-size*1.02f,x+size*.82f,y-size*.60f,paint)
        for(i in 0..5){val a=(Math.PI*2*i/6).toFloat();val px=x+kotlin.math.cos(a)*size*.46f;val py=y-size*1.38f+kotlin.math.sin(a)*size*.46f;drawSphere3D(c,px,py,size*.34f,size*.31f,petalColor)}
        drawSphere3D(c,x,y-size*1.38f,size*.23f,size*.22f,Color.rgb(255,195,55))
    }

    private fun drawHeader(c: Canvas, w: Float, h: Float) {
        paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(255,229,157);paint.textSize=w*.060f
        c.drawText("JARDIM EM BLOCOS",w*.5f,h*.068f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        paint.color=Color.rgb(183,229,194);paint.textSize=w*.021f
        c.drawText(if(activeExpedition) "EXPEDIÇÃO  •  ETAPA "+(expeditionStage+1) else "CULTIVE • COMBINE • FLORESÇA",w*.5f,h*.101f,paint)

        drawGardenShortcut(c,w,h)
        drawStat(c,w*.25f,h*.165f,"PONTOS",score.toString())
        drawStat(c,w*.69f,h*.165f,"RECORDE",max(best,score).toString())
        paint.color=Color.rgb(255,226,160);paint.textSize=w*.024f
        c.drawText("NÍVEL $level   •   ✦ $coins",w*.50f,h*.216f,paint)
        if(activeExpedition){paint.color=Color.rgb(255,235,177);paint.textSize=w*.020f;c.drawText("META "+expeditionGoals[expeditionStage],w*.5f,h*.242f,paint)}
        val lp=(score%500)/500f
        val lb=RectF(w*.25f,h*.222f,w*.75f,h*.232f)
        rounded(c,lb,6f,Color.argb(110,220,245,220))
        if(lp>0f)drawPremiumCard(c,RectF(lb.left,lb.top,lb.left+lb.width()*lp,lb.bottom),5f,Color.rgb(255,230,143),Color.rgb(232,161,63))
        val shopButton=RectF(w*.715f,h*.125f,w*.835f,h*.205f)
        drawPremiumCard(c,shopButton,17f,Color.rgb(255,220,133),Color.rgb(190,125,49))
        paint.color=Color.rgb(55,62,38);paint.textSize=w*.030f;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        c.drawText("LOJA",shopButton.centerX(),shopButton.centerY()+w*.011f,paint)
        val button=RectF(w*.855f,h*.125f,w*.965f,h*.205f)
        drawPremiumCard(c,button,17f,Color.rgb(39,92,70),Color.rgb(8,42,39))
        paint.color=Color.WHITE;paint.textSize=w*.043f
        c.drawText("Ⅱ",button.centerX(),button.centerY()+w*.014f,paint)
    }

    private fun drawGardenShortcut(c: Canvas, w: Float, h: Float) {
        val button = RectF(w * .035f, h * .125f, w * .145f, h * .205f)
        rounded(c, button, 18f, Color.argb(165, 5, 35, 29))
        paint.color = Color.rgb(255, 232, 166)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.textSize = w * .047f
        c.drawText("✿", button.centerX(), button.centerY() + w * .016f, paint)
    }

    private fun drawGardenMiniature(c: Canvas,index:Int,stage:Int,rect:RectF){
        val sky=when(index){0->Color.rgb(133,207,235);1->Color.rgb(145,205,184);else->Color.rgb(123,205,220)}
        val grass=when(index){0->Color.rgb(102,183,91);1->Color.rgb(71,151,88);else->Color.rgb(77,164,130)}
        paint.shader=LinearGradient(rect.left,rect.top,rect.left,rect.bottom,sky,Color.rgb(218,231,176),Shader.TileMode.CLAMP)
        c.drawRoundRect(rect,18f,18f,paint);paint.shader=null
        drawSphere3D(c,rect.right-rect.width()*.17f,rect.top+rect.height()*.19f,rect.width()*.075f,rect.height()*.12f,Color.rgb(255,220,116))
        val far=android.graphics.Path().apply{moveTo(rect.left,rect.top+rect.height()*.60f);cubicTo(rect.left+rect.width()*.25f,rect.top+rect.height()*.25f,rect.left+rect.width()*.50f,rect.top+rect.height()*.55f,rect.right,rect.top+rect.height()*.38f);lineTo(rect.right,rect.bottom);lineTo(rect.left,rect.bottom);close()}
        paint.color=Color.rgb(99,166,111);c.drawPath(far,paint)
        val land=android.graphics.Path().apply{moveTo(rect.left,rect.top+rect.height()*.66f);cubicTo(rect.left+rect.width()*.30f,rect.top+rect.height()*.46f,rect.left+rect.width()*.58f,rect.top+rect.height()*.75f,rect.right,rect.top+rect.height()*.52f);lineTo(rect.right,rect.bottom);lineTo(rect.left,rect.bottom);close()}
        paint.shader=LinearGradient(rect.left,rect.top+rect.height()*.5f,rect.left,rect.bottom,Color.rgb(133,201,100),grass,Shader.TileMode.CLAMP);c.drawPath(land,paint);paint.shader=null
        if(index==2){
            val water=android.graphics.Path().apply{moveTo(rect.left+rect.width()*.06f,rect.top+rect.height()*.70f);cubicTo(rect.left+rect.width()*.38f,rect.top+rect.height()*.57f,rect.left+rect.width()*.62f,rect.top+rect.height()*.85f,rect.right-rect.width()*.04f,rect.top+rect.height()*.66f);lineTo(rect.right,rect.bottom);lineTo(rect.left,rect.bottom);close()}
            paint.shader=LinearGradient(rect.left,rect.top+rect.height()*.62f,rect.left,rect.bottom,Color.rgb(105,213,226),Color.rgb(30,123,167),Shader.TileMode.CLAMP);c.drawPath(water,paint);paint.shader=null
            paint.color=Color.argb(145,230,255,249);c.drawLine(rect.left+rect.width()*.38f,rect.top+rect.height()*.73f,rect.left+rect.width()*.62f,rect.top+rect.height()*.72f,paint)
            c.drawLine(rect.left+rect.width()*.68f,rect.top+rect.height()*.80f,rect.left+rect.width()*.83f,rect.top+rect.height()*.78f,paint)
        }
        drawTree3D(c,rect.left+rect.width()*.19f,rect.top+rect.height()*.79f,rect.width()*.12f,Color.rgb(45,145,73),.66f)
        drawTree3D(c,rect.left+rect.width()*.82f,rect.top+rect.height()*.72f,rect.width()*.14f,if(index==1)Color.rgb(39,112,69)else Color.rgb(61,157,78),.80f)
        drawRock3D(c,rect.left+rect.width()*.43f,rect.top+rect.height()*.83f,rect.width()*.045f,rect.height()*.035f)
        if(stage>=1){
            for(i in 0..4){val x=rect.left+rect.width()*(.30f+i*.105f);val y=rect.top+rect.height()*(.78f-(i%2)*.05f);drawFlower3D(c,x,y,rect.width()*.025f,if(i%2==0)Color.rgb(255,117,153)else Color.rgb(255,218,91))}
        }
        if(stage>=2){
            drawTree3D(c,rect.left+rect.width()*.53f,rect.top+rect.height()*.78f,rect.width()*.15f,Color.rgb(45,157,69),1.05f)
            for(i in 0..5){val x=rect.left+rect.width()*(.10f+i*.15f);drawBush3D(c,x,rect.top+rect.height()*.92f,rect.width()*.045f)}
        }
        if(stage>=3){
            for(i in 0..10){val x=rect.left+rect.width()*(.07f+i*.085f);drawFlower3D(c,x,rect.top+rect.height()*.94f,rect.width()*.022f,when(i%4){0->Color.rgb(255,105,145);1->Color.rgb(255,222,87);2->Color.rgb(188,137,236);else->Color.WHITE})}
            for(i in 0..3){paint.color=Color.argb(180,255,245,174);c.drawCircle(rect.left+rect.width()*(.2f+i*.2f),rect.top+rect.height()*(.3f+(i%2)*.12f),rect.width()*.008f,paint)}
        }
    }

    private fun drawGardenDetailScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.045f,h*.055f,w*.955f,h*.945f),30f,Color.rgb(247,239,207),Color.rgb(207,220,181))
        val index=selectedGarden.coerceIn(0,2);val stage=gardenStages[index]
        val unlocked=index==0||gardenStages[index-1]>=3
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64);paint.textSize=w*.052f
        c.drawText(gardenNames[index],w*.5f,h*.125f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=w*.025f;paint.color=Color.rgb(174,116,43)
        c.drawText("✦  $coins MOEDAS",w*.5f,h*.17f,paint)
        val scene=RectF(w*.10f,h*.205f,w*.90f,h*.57f)
        drawPremiumCard(c,scene,23f,Color.rgb(150,204,183),Color.rgb(54,122,78))
        drawGardenMiniature(c,index,stage,RectF(scene.left+4f,scene.top+4f,scene.right-4f,scene.bottom-4f))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64);paint.textSize=w*.034f
        c.drawText("ETAPA $stage DE 3",w*.5f,h*.625f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.025f;paint.color=Color.rgb(103,111,87)
        c.drawText(gardenDescriptions[index],w*.5f,h*.67f,paint)
        val progress=RectF(w*.18f,h*.705f,w*.82f,h*.725f)
        rounded(c,progress,10f,Color.rgb(192,204,169))
        if(stage>0)drawPremiumCard(c,RectF(progress.left,progress.top,progress.left+progress.width()*stage/3f,progress.bottom),9f,Color.rgb(129,191,107),Color.rgb(59,139,76))
        val action=RectF(w*.18f,h*.755f,w*.82f,h*.835f)
        val canRestore=unlocked&&stage<3&&coins>=gardenCosts[index][stage]
        val label=when{!unlocked->"JARDIM BLOQUEADO";stage>=3->"JARDIM RESTAURADO!";else->"RESTAURAR  •  "+gardenCosts[index][stage]+" MOEDAS"}
        drawPremiumCard(c,action,19f,if(canRestore)Color.rgb(105,177,103)else Color.rgb(184,195,165),if(canRestore)Color.rgb(44,121,71)else Color.rgb(140,157,130))
        paint.color=Color.WHITE;paint.textSize=w*.028f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        c.drawText(label,action.centerX(),action.centerY()+w*.01f,paint)
        val back=RectF(w*.25f,h*.865f,w*.75f,h*.92f)
        drawPremiumCard(c,back,15f,Color.rgb(72,151,94),Color.rgb(34,103,67))
        paint.color=Color.WHITE;paint.textSize=w*.029f
        c.drawText("VOLTAR AOS JARDINS",back.centerX(),back.centerY()+w*.01f,paint)
    }

    private fun drawGardensScreen(c: Canvas, w: Float, h: Float) {
        // Mapa-múndi ilustrado: terreno contínuo, rio, trilha e regiões exploráveis.
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(157, 202, 157), Color.rgb(190, 211, 151), Color.rgb(225, 211, 153)),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Colinas e clareiras ao fundo.
        paint.color = Color.rgb(119, 174, 119)
        c.drawOval(-w*.28f, h*.12f, w*.68f, h*.53f, paint)
        c.drawOval(w*.42f, h*.30f, w*1.22f, h*.72f, paint)
        paint.color = Color.rgb(139, 190, 126)
        c.drawOval(-w*.20f, h*.48f, w*.72f, h*.88f, paint)
        c.drawOval(w*.38f, h*.62f, w*1.18f, h*.96f, paint)

        // Rio sinuoso atravessando o mundo.
        val river = android.graphics.Path().apply {
            moveTo(w*1.08f, h*.18f)
            cubicTo(w*.72f, h*.29f, w*.92f, h*.39f, w*.55f, h*.49f)
            cubicTo(w*.18f, h*.59f, w*.48f, h*.69f, w*.02f, h*.79f)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = w*.105f
        paint.color = Color.rgb(104, 177, 190)
        c.drawPath(river, paint)
        paint.strokeWidth = w*.012f
        paint.color = Color.argb(115, 224, 250, 235)
        c.drawPath(river, paint)

        // Trilha de exploração com borda escura e centro de areia.
        val trail = android.graphics.Path().apply {
            moveTo(w*.28f, h*.285f)
            cubicTo(w*.78f, h*.34f, w*.18f, h*.43f, w*.70f, h*.49f)
            cubicTo(w*.91f, h*.56f, w*.17f, h*.62f, w*.32f, h*.705f)
        }
        paint.strokeWidth = w*.052f
        paint.color = Color.rgb(119, 104, 68)
        c.drawPath(trail, paint)
        paint.strokeWidth = w*.034f
        paint.color = Color.rgb(239, 211, 145)
        c.drawPath(trail, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT

        // Pequenas árvores e pedras decorativas do mundo.
        for (i in 0..9) {
            val x = w * (0.08f + (i % 5) * .205f)
            val y = h * (0.235f + (i / 5) * .255f)
            paint.color = Color.rgb(104, 143, 78)
            c.drawCircle(x, y, w*.026f, paint)
            paint.color = Color.rgb(65, 119, 69)
            c.drawCircle(x-w*.008f, y-w*.009f, w*.020f, paint)
            paint.color = Color.rgb(116, 83, 52)
            c.drawRect(x-w*.004f, y+w*.012f, x+w*.004f, y+w*.035f, paint)
        }

        // Cabeçalho flutuante.
        rounded(c, RectF(w*.055f,h*.035f,w*.945f,h*.155f), 25f, Color.rgb(248,239,204))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47,91,64)
        paint.textSize = w*.060f
        c.drawText("MUNDO JARDIM",w*.5f,h*.092f,paint)
        paint.textSize = w*.027f
        paint.color = Color.rgb(116,105,68)
        c.drawText("Explore, restaure e desbloqueie novas terras",w*.5f,h*.132f,paint)
        rounded(c,RectF(w*.66f,h*.165f,w*.94f,h*.211f),14f,Color.rgb(255,245,210))
        paint.color = Color.rgb(174,116,43)
        paint.textSize = w*.026f
        c.drawText("✦  "+coins+" moedas",w*.80f,h*.196f,paint)

        val centersX = floatArrayOf(.28f,.70f,.32f)
        val centersY = floatArrayOf(.295f,.49f,.695f)
        val regionColors = intArrayOf(Color.rgb(244,190,112),Color.rgb(131,177,119),Color.rgb(116,190,205))
        for (i in 0..2) {
            val cx = w*centersX[i]
            val cy = h*centersY[i]
            val unlocked = i == 0 || gardenStages[i-1] >= 3
            val stage = gardenStages[i]
            val island = RectF(cx-w*.205f,cy-h*.078f,cx+w*.205f,cy+h*.078f)

            // Ilha com sombra, borda de terra e interior verde.
            paint.color = Color.argb(65,35,65,38)
            c.drawOval(island.left+w*.012f,island.top+h*.014f,island.right+w*.012f,island.bottom+h*.014f,paint)
            paint.color = Color.rgb(118,151,83)
            c.drawOval(island.left,island.top,island.right,island.bottom,paint)
            paint.color = Color.rgb(205,190,126)
            c.drawOval(island.left+w*.012f,island.top+h*.008f,island.right-w*.012f,island.bottom-h*.008f,paint)
            paint.color = regionColors[i]
            c.drawOval(island.left+w*.021f,island.top+h*.014f,island.right-w*.021f,island.bottom-h*.014f,paint)

            val scene = RectF(cx-w*.155f,cy-h*.061f,cx+w*.155f,cy+h*.040f)
            drawGardenMiniature(c,i,stage,scene)

            // Faixa com nome e estado da região.
            val namePlate = RectF(cx-w*.17f,cy+h*.040f,cx+w*.17f,cy+h*.073f)
            rounded(c,namePlate,10f,Color.rgb(250,244,218))
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-rounded",Typeface.BOLD)
            paint.textSize = w*.025f
            paint.color = Color.rgb(47,91,64)
            c.drawText(gardenNames[i],cx,cy+h*.062f,paint)

            // Medalhão de progresso.
            paint.color = if (!unlocked) Color.rgb(104,108,93) else if (stage>=3) Color.rgb(230,174,59) else Color.rgb(68,132,78)
            c.drawCircle(cx+w*.157f,cy-h*.050f,w*.036f,paint)
            paint.color = Color.WHITE
            paint.textSize = w*.034f
            c.drawText(if(!unlocked)"×" else if(stage>=3)"★" else (stage+1).toString(),cx+w*.157f,cy-h*.038f,paint)

            if (!unlocked) {
                paint.color = Color.argb(135,35,47,38)
                c.drawOval(island.left,island.top,island.right,island.bottom,paint)
                paint.color = Color.WHITE
                paint.textSize = w*.025f
                c.drawText("CONCLUA A REGIÃO ANTERIOR",cx,cy+h*.012f,paint)
            }
        }

        // Painel compacto de missões e recompensas.
        rounded(c,RectF(w*.055f,h*.785f,w*.945f,h*.885f),20f,Color.rgb(248,239,204))
        paint.textAlign=Paint.Align.LEFT
        paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64)
        paint.textSize=w*.025f
        c.drawText("MISSÃO DIÁRIA  •  "+dailyLines+"/5 linhas",w*.09f,h*.818f,paint)
        c.drawText("MISSÃO SEMANAL  •  "+weeklyLines+"/20 linhas",w*.09f,h*.858f,paint)
        val dr=dailyLines>=5&&!dailyRewardClaimed
        val wr=weeklyLines>=20&&!weeklyRewardClaimed
        rounded(c,RectF(w*.70f,h*.795f,w*.90f,h*.835f),10f,if(dr)Color.rgb(83,151,91)else Color.rgb(188,199,172))
        rounded(c,RectF(w*.70f,h*.838f,w*.90f,h*.878f),10f,if(wr)Color.rgb(83,151,91)else Color.rgb(188,199,172))
        paint.textAlign=Paint.Align.CENTER
        paint.textSize=w*.019f
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        paint.color=if(dr)Color.WHITE else Color.rgb(76,91,66)
        c.drawText(if(dailyRewardClaimed)"RESGATADA"else"+100 MOEDAS",w*.80f,h*.821f,paint)
        paint.color=if(wr)Color.WHITE else Color.rgb(76,91,66)
        c.drawText(if(weeklyRewardClaimed)"RESGATADA"else"+250 MOEDAS",w*.80f,h*.864f,paint)
        rounded(c,RectF(w*.27f,h*.90f,w*.73f,h*.955f),16f,Color.rgb(57,119,70))
        paint.color=Color.WHITE
        paint.textSize=w*.028f
        c.drawText("VOLTAR AO JOGO",w*.5f,h*.936f,paint)
    }
    private fun refreshDailyMission() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDay = prefs.getString("daily_mission_day", "")
        if (savedDay != today) {
            dailyLines = 0
            dailyRewardClaimed = false
            prefs.edit().putString("daily_mission_day", today)
                .putInt("daily_lines", 0).putBoolean("daily_reward_claimed", false).apply()
        }
    }

    private fun refreshWeeklyMission() {
        val week = SimpleDateFormat("YYYYww", Locale.US).format(Date())
        val savedWeek = prefs.getString("weekly_mission_week", "")
        if (savedWeek != week) {
            weeklyLines = 0
            weeklyRewardClaimed = false
            prefs.edit().putString("weekly_mission_week", week)
                .putInt("weekly_lines", 0).putBoolean("weekly_reward_claimed", false).apply()
        }
    }

    private fun saveGardenProgress() {
        val editor = prefs.edit().putInt("garden_coins", coins)
            .putInt("daily_lines", dailyLines)
            .putBoolean("daily_reward_claimed", dailyRewardClaimed)
            .putInt("weekly_lines", weeklyLines)
            .putBoolean("weekly_reward_claimed", weeklyRewardClaimed)
        for (i in 0..2) editor.putInt("garden_stage_$i", gardenStages[i])
        editor.apply()
    }

    private fun restoreGarden(index: Int) {
        if (index !in 0..2) return
        val unlocked = index == 0 || gardenStages[index - 1] >= 3
        val stage = gardenStages[index]
        if (!unlocked || stage >= 3) return
        val cost = gardenCosts[index][stage]
        if (coins >= cost) {
            coins -= cost
            gardenStages[index]++
            saveGardenProgress()
            playTone(ToneGenerator.TONE_PROP_ACK, 120)
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        } else {
            playTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }

    private fun drawStat(c: Canvas, x: Float, y: Float, label: String, value: String) {
        val box=RectF(x-width*.17f,y-height*.026f,x+width*.17f,y+height*.047f)
        drawPremiumCard(c,box,height*.018f,Color.argb(225,16,68,57),Color.argb(220,6,38,37))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        paint.color=Color.rgb(177,226,190);paint.textSize=width*.021f
        c.drawText(label,x,y,paint)
        paint.color=Color.WHITE;paint.textSize=width*.041f
        c.drawText(value,x,y+height*.039f,paint)
    }

    private fun drawBoard(c: Canvas) {
        val outer=RectF(boardLeft-10f,boardTop-10f,boardLeft+boardSize+10f,boardTop+boardSize+10f)
        paint.color=Color.argb(100,0,0,0);c.drawRoundRect(RectF(outer.left,outer.top+8f,outer.right,outer.bottom+8f),27f,27f,paint)
        drawPremiumCard(c,outer,26f,Color.rgb(241,205,126),Color.rgb(143,91,43))
        val inner=RectF(outer.left+5f,outer.top+5f,outer.right-5f,outer.bottom-5f)
        rounded(c,inner,22f,Color.rgb(12,48,43))
        for(r in 0..8)for(col in 0..8){
            val left=boardLeft+col*cell;val top=boardTop+r*cell
            val rect=RectF(left+2.2f,top+2.2f,left+cell-2.2f,top+cell-2.2f)
            if(board[r][col]){
                drawBlock(c,rect,colors[r][col],cell*.15f)
            }else{
                rounded(c,rect,cell*.14f,Color.argb(105,142,194,160))
                paint.color=Color.argb(30,235,255,235)
                c.drawRoundRect(RectF(rect.left+cell*.10f,rect.top+cell*.08f,rect.right-cell*.10f,rect.top+cell*.13f),cell*.05f,cell*.05f,paint)
            }
        }
        if(dragging&&previewRow>=0&&previewCol>=0){
            val p=pieces.getOrNull(selected)
            if(p!=null)p.cells.forEach{(dx,dy)->
                val row=previewRow+dy;val col=previewCol+dx
                if(row in 0..8&&col in 0..8){
                    val rect=RectF(boardLeft+col*cell+2f,boardTop+row*cell+2f,boardLeft+(col+1)*cell-2f,boardTop+(row+1)*cell-2f)
                    drawBlock(c,rect,if(previewValid)p.color else Color.rgb(235,75,75),cell*.15f,if(previewValid)155 else 175)
                }
            }
        }
    }

    private fun drawBlock(c: Canvas, rect: RectF, base: Int, radius: Float, alpha: Int = 255) {
        val d=min(rect.width(),rect.height())*.17f
        // Sombra projetada e faces laterais: cada peça é um pequeno cubo.
        paint.color=Color.argb(alpha/2,5,24,22)
        c.drawRoundRect(RectF(rect.left+d*.35f,rect.top+d*1.45f,rect.right+d*.7f,rect.bottom+d*1.45f),radius,radius,paint)
        val right=android.graphics.Path().apply{moveTo(rect.right-d,rect.top+d*.48f);lineTo(rect.right,rect.top);lineTo(rect.right,rect.bottom-d*.55f);lineTo(rect.right-d,rect.bottom);close()}
        paint.color=withAlpha(Color.rgb(max(0,Color.red(base)-65),max(0,Color.green(base)-65),max(0,Color.blue(base)-65)),alpha);c.drawPath(right,paint)
        val bottom=android.graphics.Path().apply{moveTo(rect.left+d,rect.bottom-d*.55f);lineTo(rect.right,rect.bottom-d*.55f);lineTo(rect.right-d,rect.bottom);lineTo(rect.left,rect.bottom);close()}
        paint.color=withAlpha(Color.rgb(max(0,Color.red(base)-85),max(0,Color.green(base)-85),max(0,Color.blue(base)-85)),alpha);c.drawPath(bottom,paint)
        val top=android.graphics.Path().apply{moveTo(rect.left+d,rect.top);lineTo(rect.right,rect.top);lineTo(rect.right-d,rect.top+d);lineTo(rect.left,rect.top+d);close()}
        paint.shader=LinearGradient(rect.left,rect.top,rect.right,rect.top,withAlpha(Color.rgb(min(255,Color.red(base)+55),min(255,Color.green(base)+55),min(255,Color.blue(base)+55)),alpha),withAlpha(base,alpha),Shader.TileMode.CLAMP)
        c.drawPath(top,paint);paint.shader=null
        paint.color=withAlpha(Color.argb(90,255,255,255),alpha)
        c.drawLine(rect.left+d,rect.top+1f,rect.right-2f,rect.top+1f,paint)
        c.drawLine(rect.left+d*.45f,rect.top+d*.45f,rect.left+d,rect.top+1f,paint)
        paint.color=withAlpha(Color.argb(65,255,255,255),alpha)
        c.drawCircle(rect.left+d*1.25f,rect.top+d*.48f,d*.12f,paint)
    }

    private fun drawPiece(c: Canvas, piece: Piece, cx: Float, cy: Float, size: Float) {
        val maxX=piece.cells.maxOf{it.first}+1;val maxY=piece.cells.maxOf{it.second}+1
        val unit=min(size*2.35f/maxX,size*2.35f/maxY)
        piece.cells.forEach{(x,y)->
            val left=cx-maxX*unit/2f+x*unit;val top=cy-maxY*unit/2f+y*unit
            drawBlock(c,RectF(left,top,left+unit-3f,top+unit-3f),piece.color,7f)
        }
    }

    private fun drawPieceTray(c: Canvas, w: Float, h: Float) {
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        paint.color=Color.rgb(255,231,170);paint.textSize=w*.030f
        c.drawText("ESCOLHA SUA PRÓXIMA JOGADA",w*.5f,slotsTop-h*.020f,paint)
        val slotWidth=w*.275f
        for(i in 0..2){
            val centerX=w*(.18f+i*.32f);val active=i==selected&&pieces[i]!=null
            val rect=RectF(centerX-slotWidth/2f,trayCenterY-h*.047f,centerX+slotWidth/2f,trayCenterY+h*.047f)
            if(pieces[i]==null)drawPremiumCard(c,rect,19f,Color.argb(80,13,42,37),Color.argb(65,7,30,29))
            else if(active)drawPremiumCard(c,rect,19f,Color.rgb(255,231,164),Color.rgb(211,153,75))
            else drawPremiumCard(c,rect,19f,Color.rgb(38,104,78),Color.rgb(9,52,46))
            if(active){paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.color=Color.rgb(255,245,194);c.drawRoundRect(rect,19f,19f,paint);paint.style=Paint.Style.FILL}
            val piece=pieces[i]
            if(piece!=null&&!(dragging&&i==selected))drawPiece(c,piece,centerX,trayCenterY-h*.004f,min(cell*.37f,h*.025f))
            else if(piece==null){paint.color=Color.argb(150,235,247,232);paint.textSize=w*.045f;c.drawText("✓",centerX,trayCenterY+h*.012f,paint)}
        }
        if(comboAlpha>0f){paint.color=withAlpha(Color.rgb(255,225,137),(comboAlpha*255).toInt());paint.textSize=w*.043f;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD);c.drawText(comboLabel,w*.5f,trayCenterY+h*.105f,paint)}
    }

    private fun drawFooter(c: Canvas, w: Float, h: Float) {
        paint.color = if (activePower.isNotEmpty()) Color.rgb(255, 226, 160) else Color.rgb(218, 241, 220)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.textSize = w * .025f
        val hint = when (activePower) {
            "hammer" -> "MARTELO ATIVO • TOQUE EM UM BLOCO"
            "row" -> "BOMBA DE LINHA ATIVA • TOQUE NA LINHA"
            else -> "Arraste uma peça para o tabuleiro"
        }
        c.drawText(hint, w / 2f, h * .955f, paint)
    }

    private fun drawShopScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.045f,h*.055f,w*.955f,h*.945f),30f,Color.rgb(247,239,207),Color.rgb(213,218,179))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD);paint.color=Color.rgb(46,91,66);paint.textSize=w*.060f
        c.drawText("ARSENAL DO JARDINEIRO",w*.5f,h*.125f,paint)
        paint.textSize=w*.026f;paint.color=Color.rgb(156,105,45);c.drawText("✦  $coins MOEDAS DISPONÍVEIS",w*.5f,h*.17f,paint)
        val names=listOf("Martelo","Bomba de linha","Nova mão")
        val descriptions=listOf("Destrói um bloco escolhido","Libera uma linha inteira","Renova suas três peças")
        val counts=intArrayOf(hammerCount,rowBombCount,rerollCount);val prices=intArrayOf(80,120,60)
        val icons=listOf("⚒","✹","↻");val hues=intArrayOf(Color.rgb(213,142,74),Color.rgb(202,94,73),Color.rgb(76,151,166))
        for(i in 0..2){
            val top=h*(.225f+i*.185f);drawPremiumCard(c,RectF(w*.085f,top,w*.915f,top+h*.155f),20f,Color.rgb(255,252,231),Color.rgb(230,229,194))
            paint.color=hues[i];c.drawCircle(w*.19f,top+h*.075f,w*.052f,paint);paint.color=Color.WHITE;paint.textSize=w*.052f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);c.drawText(icons[i],w*.19f,top+h*.093f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD);paint.color=Color.rgb(47,91,64);paint.textSize=w*.035f;c.drawText(names[i],w*.29f,top+h*.055f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.color=Color.rgb(103,111,87);paint.textSize=w*.021f;c.drawText(descriptions[i],w*.29f,top+h*.090f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.color=Color.rgb(164,111,43);paint.textSize=w*.024f;c.drawText("NO INVENTÁRIO: "+counts[i],w*.29f,top+h*.128f,paint)
            val btn=RectF(w*.68f,top+h*.045f,w*.87f,top+h*.115f);drawPremiumCard(c,btn,12f,if(counts[i]>0)Color.rgb(93,170,105)else Color.rgb(242,196,91),if(counts[i]>0)Color.rgb(48,119,73)else Color.rgb(198,135,48))
            paint.textAlign=Paint.Align.CENTER;paint.color=Color.WHITE;paint.textSize=w*.023f;c.drawText(if(counts[i]>0)"USAR"else prices[i].toString()+" ✦",btn.centerX(),btn.centerY()+w*.008f,paint)
        }
        val back=RectF(w*.23f,h*.825f,w*.77f,h*.90f);drawPremiumCard(c,back,18f,Color.rgb(67,151,91),Color.rgb(33,103,67))
        paint.textAlign=Paint.Align.CENTER;paint.color=Color.WHITE;paint.textSize=w*.029f;c.drawText("VOLTAR AO JOGO",w*.5f,h*.87f,paint)
    }

    private fun savePowerInventory() {
        prefs.edit().putInt("power_hammer",hammerCount)
            .putInt("power_row_bomb",rowBombCount)
            .putInt("power_reroll",rerollCount).apply()
        saveGardenProgress()
    }

    private fun activateShopItem(index:Int) {
        val counts=intArrayOf(hammerCount,rowBombCount,rerollCount)
        val prices=intArrayOf(80,120,60)
        if(counts[index]>0){
            when(index){
                0->{activePower="hammer";showShop=false}
                1->{activePower="row";showShop=false}
                2->{rerollCount--;showShop=false;pieces=MutableList(3){newPiece()};selected=0;gameOver=false;emitBurst(width*.5f,height*.52f,Color.rgb(255,220,125),28,1.7f);animateEffects();playPowerSound("reroll")}
            }
            savePowerInventory()
            playTone(ToneGenerator.TONE_PROP_ACK,130)
            comboLabel=when(index){0->"MARTELO ATIVO";1->"BOMBA DE LINHA ATIVA";else->"NOVAS PEÇAS!"}
        } else if(coins>=prices[index]){
            coins-=prices[index]
            when(index){0->hammerCount++;1->rowBombCount++;2->rerollCount++}
            savePowerInventory()
            playTone(ToneGenerator.TONE_PROP_ACK,130)
        } else {
            playTone(ToneGenerator.TONE_PROP_BEEP,90)
        }
    }

    private fun usePowerAt(x:Float,y:Float) {
        if (activePower.isEmpty()) return
        if (x < boardLeft || x >= boardLeft + boardSize || y < boardTop || y >= boardTop + boardSize) {
            playTone(ToneGenerator.TONE_PROP_BEEP, 55)
            return
        }
        val row = ((y - boardTop) / cell).toInt().coerceIn(0, 8)
        val col = ((x - boardLeft) / cell).toInt().coerceIn(0, 8)
        val powerUsed = activePower
        var removed = 0
        if (powerUsed == "hammer") {
            if (board[row][col]) {
                emitBurst(boardLeft + (col + .5f) * cell, boardTop + (row + .5f) * cell, colors[row][col], 18, 1.25f)
                board[row][col] = false
                colors[row][col] = Color.TRANSPARENT
                removed = 1
            }
        } else if (powerUsed == "row") {
            for (c in 0..8) if (board[row][c]) {
                emitBurst(boardLeft + (c + .5f) * cell, boardTop + (row + .5f) * cell, colors[row][c], 9, 1.1f)
                board[row][c] = false
                colors[row][c] = Color.TRANSPARENT
                removed++
            }
        }
        if (removed > 0) {
            score += removed * 10
            if (score > best) { best = score; prefs.edit().putInt("best_score", best).apply() }
            level = 1 + score / 500
            if (powerUsed == "hammer") hammerCount-- else rowBombCount--
            activePower = ""
            savePowerInventory()
            animateEffects()
            playPowerSound(powerUsed)
            gameOver = !availablePiecesHaveMove()
        } else {
            // O item continua disponível se a célula ou linha estiver vazia.
            playTone(ToneGenerator.TONE_PROP_BEEP, 65)
        }
        invalidate()
    }

    private fun drawSparks(c: Canvas) {
        val t = effectProgress
        blastCenters.forEach { blast ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, cell * .055f * (1f - t * .45f))
            paint.color = withAlpha(blast.color, (190f * (1f - t)).toInt().coerceIn(0, 190))
            c.drawCircle(blast.x, blast.y, cell * (.25f + t * 2.1f), paint)
            paint.style = Paint.Style.FILL
        }
        sparks.forEach { particle ->
            val px = particle.x + particle.vx * t
            val py = particle.y + particle.vy * t + height * .12f * t * t
            paint.color = withAlpha(particle.color, (255f * (1f - t)).toInt().coerceIn(0, 255))
            c.save()
            c.rotate(particle.rotation + t * 250f, px, py)
            val half = particle.size * (1f - t * .45f)
            c.drawRoundRect(RectF(px-half, py-half*.65f, px+half, py+half*.65f), half*.18f, half*.18f, paint)
            c.restore()
        }
        paint.style = Paint.Style.FILL
    }

    private fun emitBurst(x: Float, y: Float, color: Int, amount: Int = 12, spread: Float = 1f) {
        blastCenters = (blastCenters + Blast(x, y, color)).takeLast(24)
        val fragments = (0 until amount).map {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = cell * (.35f + Random.nextFloat() * 1.15f) * spread
            Spark(x, y, color, cell * (.055f + Random.nextFloat() * .07f),
                kotlin.math.cos(angle) * speed, kotlin.math.sin(angle) * speed - cell * .22f,
                Random.nextFloat() * 180f)
        }
        sparks = (sparks + fragments).takeLast(220)
    }

    private fun drawDim(c: Canvas, w: Float, h: Float) {
        paint.shader=LinearGradient(0f,0f,0f,h,intArrayOf(Color.argb(225,3,20,23),Color.argb(195,5,36,31)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint);paint.shader=null
    }

    private fun drawPanel(c: Canvas, w: Float, top: Float, bottom: Float) {
        drawPremiumCard(c,RectF(w*.055f,top,w*.945f,bottom),30f,Color.rgb(248,241,211),Color.rgb(211,220,184))
    }

    private fun drawTutorial(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .30f, h * .68f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .060f
        c.drawText("Como jogar", w / 2f, h * .385f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .035f
        c.drawText("1. Escolha uma das três peças.", w / 2f, h * .455f, paint)
        c.drawText("2. Arraste para um espaço livre.", w / 2f, h * .505f, paint)
        c.drawText("3. Complete linhas ou colunas.", w / 2f, h * .555f, paint)
        c.drawText("Use todas as peças para receber outras.", w / 2f, h * .605f, paint)
        drawButton(c, w, h * .625f, "COMEÇAR", true)
    }

    private fun drawPauseMenu(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .25f, h * .77f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .061f
        c.drawText("Jogo pausado", w / 2f, h * .355f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .032f
        c.drawText("Partidas: $gamesPlayed   •   Linhas: $totalLines", w / 2f, h * .405f, paint)
        c.drawText("Melhor combo: $bestCombo linhas", w / 2f, h * .445f, paint)
        drawButton(c, w, h * .505f, "CONTINUAR", true)
        drawButton(c, w, h * .585f, if (soundEnabled) "SOM: LIGADO" else "SOM: DESLIGADO", false)
        drawButton(c, w, h * .635f, "REINICIAR PARTIDA", false)
        drawButton(c, w, h * .715f, "MENU PRINCIPAL", false)
    }

    private fun drawRestartConfirmation(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .37f, h * .62f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .050f
        c.drawText("Recomeçar partida?", w / 2f, h * .445f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .031f
        c.drawText("Seu progresso nesta partida será perdido.", w / 2f, h * .49f, paint)
        drawButton(c, w, h * .535f, "SIM, RECOMEÇAR", true)
        drawButton(c, w, h * .595f, "CANCELAR", false)
    }

    private fun drawGameOver(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .34f, h * .66f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .066f
        c.drawText("Fim de jogo", w / 2f, h * .415f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .035f
        c.drawText("Pontuação: $score", w / 2f, h * .465f, paint)
        c.drawText("Recorde: $best", w / 2f, h * .505f, paint)
        if (activeExpedition) {
            paint.color = Color.rgb(174,116,43)
            paint.textSize = w*.027f
            c.drawText("Meta da etapa: "+expeditionGoals[expeditionStage]+" pontos",w/2f,h*.535f,paint)
        }
        drawButton(c, w, h * .565f, if(activeExpedition) "TENTAR NOVAMENTE" else "JOGAR NOVAMENTE", true)
        drawButton(c, w, h * .655f, "MENU PRINCIPAL", false)
    }

    private fun drawButton(c: Canvas, w: Float, centerY: Float, label: String, primary: Boolean) {
        val rect=RectF(w*.17f,centerY-height*.030f,w*.83f,centerY+height*.030f)
        if(primary)drawPremiumCard(c,rect,20f,Color.rgb(255,229,151),Color.rgb(205,143,52))
        else drawPremiumCard(c,rect,20f,Color.rgb(83,151,103),Color.rgb(34,101,68))
        paint.color=if(primary)Color.rgb(61,66,39)else Color.WHITE
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        paint.textSize=w*.033f
        c.drawText(label,rect.centerX(),rect.centerY()+w*.011f,paint)
    }

    private fun drawPremiumCard(c: Canvas, rect: RectF, radius: Float, topColor: Int, bottomColor: Int) {
        paint.shader=null;paint.style=Paint.Style.FILL
        paint.color=Color.argb(85,0,0,0)
        c.drawRoundRect(RectF(rect.left+2f,rect.top+5f,rect.right+2f,rect.bottom+5f),radius,radius,paint)
        paint.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,topColor,bottomColor,Shader.TileMode.CLAMP)
        c.drawRoundRect(rect,radius,radius,paint);paint.shader=null
        paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1.5f,width*.0025f)
        paint.color=Color.argb(115,255,244,194)
        c.drawRoundRect(RectF(rect.left+1f,rect.top+1f,rect.right-1f,rect.bottom-1f),radius,radius,paint)
        paint.style=Paint.Style.FILL
        paint.color=Color.argb(38,255,255,255)
        c.drawRoundRect(RectF(rect.left+radius*.55f,rect.top+radius*.22f,rect.right-radius*.55f,rect.top+radius*.22f+max(2f,rect.height()*.025f)),radius,radius,paint)
    }

    private fun rounded(c: Canvas, rect: RectF, radius: Float, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRoundRect(rect, radius, radius, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun fits(piece: Piece, row: Int, col: Int): Boolean = piece.cells.all { (dx, dy) ->
        row + dy in 0..8 && col + dx in 0..8 && !board[row + dy][col + dx]
    }

    private fun hasAnyMove(piece: Piece): Boolean =
        (0..8).any { row -> (0..8).any { col -> fits(piece, row, col) } }

    private fun availablePiecesHaveMove(): Boolean =
        pieces.filterNotNull().any { hasAnyMove(it) }

    private fun playTone(toneType: Int, duration: Int = 100) {
        if (soundEnabled) {
            try { tone.startTone(toneType, duration) } catch (_: Exception) { }
        }
    }

    private fun playPowerSound(power: String) {
        if (!soundEnabled) return
        try {
            when (power) {
                "hammer" -> { tone.startTone(ToneGenerator.TONE_PROP_NACK, 115); performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
                "row" -> { tone.startTone(ToneGenerator.TONE_PROP_ACK, 260); performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }
                "reroll" -> { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90); performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
                "line" -> { tone.startTone(ToneGenerator.TONE_PROP_ACK, 230); performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }
            }
        } catch (_: Exception) { }
    }

    private fun placePiece(piece: Piece, row: Int, col: Int) {
        piece.cells.forEach { (dx, dy) ->
            board[row + dy][col + dx] = true
            colors[row + dy][col + dx] = piece.color
        }
        score += piece.cells.size * 5
        val fullRows = (0..8).filter { r -> (0..8).all { board[r][it] } }
        val fullCols = (0..8).filter { c -> (0..8).all { r -> board[r][c] } }
        val clearedCells = linkedSetOf<Pair<Int, Int>>()
        fullRows.forEach { r -> (0..8).forEach { c -> clearedCells.add(r to c) } }
        fullCols.forEach { colIndex -> (0..8).forEach { r -> clearedCells.add(r to colIndex) } }

        val lines = fullRows.size + fullCols.size
        coins += piece.cells.size
        if (lines > 0) {
            coins += lines * 25
            refreshDailyMission()
            dailyLines += lines
            refreshWeeklyMission()
            weeklyLines += lines
            saveGardenProgress()
            clearedCells.forEach { (r, c) ->
                emitBurst(boardLeft + (c + .5f) * cell, boardTop + (r + .5f) * cell, colors[r][c], 5, .8f)
                board[r][c] = false
                colors[r][c] = Color.TRANSPARENT
            }
            val linePoints = lines * 100 + if (lines > 1) (lines - 1) * 75 else 0
            val comboBonus = if (lastMoveTime > 0L && System.currentTimeMillis() - lastMoveTime < 4500L) {
                min(lastComboLines, 4) * 25
            } else 0
            score += linePoints + comboBonus
            totalLines += lines
            bestCombo = max(bestCombo, lines)
            lastComboLines = lines
            lastMoveTime = System.currentTimeMillis()
            prefs.edit().putInt("total_lines", totalLines).putInt("best_combo", bestCombo).apply()
            comboLabel = when {
                lines >= 4 -> "INCRÍVEL! $lines LINHAS!"
                lines == 3 -> "MEGA COMBO! +$linePoints"
                lines == 2 -> "ÓTIMO COMBO! +$linePoints"
                else -> "+$linePoints"
            }
            comboAlpha = 1f
            animateEffects()
            playPowerSound("line")
        } else {
            playTone(ToneGenerator.TONE_PROP_BEEP, 55)
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        saveGardenProgress()

        if (score > best) {
            best = score
            prefs.edit().putInt("best_score", best).apply()
        }
        level = 1 + score / 500
        pieces[selected] = null
        if (pieces.all { it == null }) {
            pieces = MutableList(3) { newPiece() }
            selected = 0
        } else if (pieces[selected] == null) {
            selected = pieces.indexOfFirst { it != null }.coerceAtLeast(0)
        }
        if (activeExpedition && score >= expeditionGoals[expeditionStage]) {
            activeExpedition = false
            showExpeditionComplete = true
            coins += expeditionRewards[expeditionStage]
            expeditionUnlocked = max(expeditionUnlocked, expeditionStage + 1).coerceAtMost(5)
            prefs.edit().putInt("expedition_unlocked", expeditionUnlocked).apply()
            saveGardenProgress()
            playTone(ToneGenerator.TONE_PROP_ACK, 220)
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        }
        gameOver = !showExpeditionComplete && !availablePiecesHaveMove()
    }

    private fun animateEffects() {
        effectProgress = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                effectProgress = it.animatedValue as Float
                comboAlpha = 1f - effectProgress
                if (effectProgress >= 1f) { sparks = emptyList(); blastCenters = emptyList() }
                postInvalidateOnAnimation()
            }
            start()
        }
        sparks = sparks.takeLast(90)
    }

    private fun resetGame() {
        for (r in 0..8) for (c in 0..8) {
            board[r][c] = false
            colors[r][c] = Color.TRANSPARENT
        }
        score = 0
        level = 1
        gameOver = false
        paused = false
        confirmRestart = false
        pieces = MutableList(3) { newPiece() }
        selected = 0
        sparks = emptyList()
        blastCenters = emptyList()
        comboAlpha = 0f
        lastComboLines = 0
        lastMoveTime = 0L
        gamesPlayed++
        prefs.edit().putInt("games_played", gamesPlayed).apply()
    }

    private fun choosePieceAt(x: Float) {
        val index = ((x / width) * 3f).toInt().coerceIn(0, 2)
        if (pieces[index] != null) selected = index
    }

    private fun updatePreview(x: Float, y: Float) {
        val piece = pieces.getOrNull(selected) ?: run {
            previewRow = -1
            previewCol = -1
            previewValid = false
            return
        }
        if (x in boardLeft..(boardLeft + boardSize) && y in boardTop..(boardTop + boardSize)) {
            val pieceWidth = (piece.cells.maxOf { it.first } + 1)
            val pieceHeight = (piece.cells.maxOf { it.second } + 1)
            previewCol = ((x - boardLeft) / cell - pieceWidth / 2f).toInt()
            previewRow = ((y - boardTop) / cell - pieceHeight / 2f).toInt()
            previewValid = fits(piece, previewRow, previewCol)
        } else {
            previewRow = -1
            previewCol = -1
            previewValid = false
        }
    }

    private fun finishTutorial() {
        showTutorial = false
        prefs.edit().putBoolean("tutorial_seen", true).apply()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (mainMenuOpen || showTutorial || paused || gameOver || confirmRestart || showGardens || showShop || showExpeditionComplete) return true
            if (activePower.isNotEmpty()) {
                dragging = false
                previewRow = -1
                previewCol = -1
                invalidate()
                return true
            }
            if (event.y < height * .22f && event.x > width * .82f) return true
            if (event.y > trayCenterY - height * .06f && event.y < trayCenterY + height * .06f) {
                choosePieceAt(event.x)
                if (pieces[selected] != null) {
                    dragging = true
                    updatePreview(event.x, event.y)
                }
                invalidate()
                return true
            }
            if (event.x in boardLeft..(boardLeft + boardSize) && event.y in boardTop..(boardTop + boardSize)) {
                dragging = true
                updatePreview(event.x, event.y)
            }
            invalidate()
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (activePower.isNotEmpty()) return true
                if (dragging) {
                    updatePreview(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (activePower.isNotEmpty()) {
                    dragging = false
                    previewRow = -1
                    previewCol = -1
                    usePowerAt(event.x, event.y)
                    invalidate()
                    return true
                }
                if (mainMenuOpen) {
                    if (showExpeditions) {
                        if (event.y > height*.80f) {
                            showExpeditions = false
                        } else {
                            val idx=((event.y/height-.205f)/.118f).toInt()
                            if (idx in 0..4 && idx == expeditionUnlocked) {
                                expeditionStage = idx
                                activeExpedition = true
                                showExpeditions = false
                                showAchievements = false
                                mainMenuOpen = false
                                resetGame()
                                if (showTutorial) finishTutorial()
                            }
                        }
                    } else if (showAchievements) {
                        if (event.y > height*.80f) showAchievements=false
                        else {
                            val idx=((event.y/height-.195f)/.102f).toInt()
                            if(idx in 0..5) {
                                val unlocked=when(idx){0->gamesPlayed>=1;1->totalLines>=10;2->best>=500;3->bestCombo>=3;4->totalLines>=50;else->gardenStages.sum()>=3}
                                val key=listOf("ach_first","ach_lines","ach_score","ach_combo","ach_master_lines","ach_world")[idx]
                                val reward=intArrayOf(50,75,100,100,150,200)[idx]
                                if(unlocked&&!prefs.getBoolean(key,false)){coins+=reward;prefs.edit().putBoolean(key,true).apply();saveGardenProgress();playTone(ToneGenerator.TONE_PROP_ACK,160)}
                            }
                        }
                    } else {
                        when {
                            event.y in height*.64f..height*.745f -> {mainMenuOpen=false;showExpeditions=false;activeExpedition=false;if(showTutorial)finishTutorial()}
                            event.y in height*.75f..height*.85f && event.x<width*.36f -> {mainMenuOpen=false;showGardens=true}
                            event.y in height*.75f..height*.85f && event.x in width*.36f..width*.64f -> showExpeditions=true
                            event.y in height*.75f..height*.85f && event.x>width*.64f -> showAchievements=true
                        }
                    }
                    invalidate(); return true
                }
                if (showExpeditionComplete) {
                    if (event.y > height*.59f) {
                        showExpeditionComplete = false
                        mainMenuOpen = true
                        showExpeditions = true
                    }
                    invalidate()
                    return true
                }
                if (showTutorial) {
                    finishTutorial()
                    return true
                }
                if (showShop) {
                    if (event.y > height*.81f) {
                        showShop=false
                    } else {
                        val idx=((event.y/height-.225f)/.185f).toInt()
                        if(idx in 0..2) activateShopItem(idx)
                    }
                    invalidate()
                    return true
                }
                if (showGardens) {
                    if (gardenDetailOpen) {
                        when {
                            event.y in height * .85f..height * .94f -> gardenDetailOpen = false
                            event.y in height * .74f..height * .85f -> {
                                val index = selectedGarden
                                if ((index == 0 || gardenStages[index - 1] >= 3) && gardenStages[index] < 3) restoreGarden(index)
                            }
                        }
                    } else {
                        when {
                            event.y in height * .89f..height * .945f -> showGardens = false
                            event.y in height * .785f..height * .835f && event.x > width * .68f && dailyLines >= 5 && !dailyRewardClaimed -> {
                                dailyRewardClaimed = true
                                coins += 100
                                saveGardenProgress()
                                playTone(ToneGenerator.TONE_PROP_ACK, 150)
                            }
                            event.y in height * .838f..height * .88f && event.x > width * .68f && weeklyLines >= 20 && !weeklyRewardClaimed -> {
                                weeklyRewardClaimed = true
                                coins += 250
                                saveGardenProgress()
                                playTone(ToneGenerator.TONE_PROP_ACK, 180)
                            }
                            event.y in height * .21f..height * .37f -> { selectedGarden = 0; gardenDetailOpen = true }
                            event.y in height * .405f..height * .575f -> { selectedGarden = 1; gardenDetailOpen = true }
                            event.y in height * .61f..height * .77f -> { selectedGarden = 2; gardenDetailOpen = true }
                        }
                    }
                    invalidate()
                    return true
                }
                if (confirmRestart) {
                    if (event.y in height * .50f..height * .57f) resetGame()
                    else if (event.y in height * .57f..height * .64f) confirmRestart = false
                    invalidate()
                    return true
                }
                if (paused) {
                    when {
                        event.y in height * .47f..height * .54f -> {
                            paused = false
                            playTone(ToneGenerator.TONE_PROP_ACK, 70)
                        }
                        event.y in height * .55f..height * .62f -> {
                            soundEnabled = !soundEnabled
                            prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
                        }
                        event.y in height * .61f..height * .68f -> confirmRestart = true
                        event.y in height * .69f..height * .76f -> { paused = false; mainMenuOpen = true }
                    }
                    invalidate()
                    return true
                }
                if (gameOver) {
                    if (event.y in height * .55f..height * .63f) resetGame()
                    else if (event.y in height * .65f..height * .73f) { gameOver = false; mainMenuOpen = true }
                    invalidate()
                    return true
                }
                if (dragging) {
                    updatePreview(event.x, event.y)
                    val piece = pieces.getOrNull(selected)
                    if (piece != null && previewValid && previewRow >= 0 && previewCol >= 0) {
                        placePiece(piece, previewRow, previewCol)
                    }
                    dragging = false
                    previewRow = -1
                    previewCol = -1
                    invalidate()
                    return true
                }
                if (event.y < height * .22f && event.x < width * .16f) {
                    showGardens = true
                    invalidate()
                    return true
                }
                if (event.y in height*.12f..height*.21f && event.x in width*.70f..width*.845f) {
                    showShop=true
                    invalidate()
                    return true
                }
                if (event.y < height * .22f && event.x > width * .82f) {
                    paused = true
                    playTone(ToneGenerator.TONE_PROP_BEEP, 70)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                previewRow = -1
                previewCol = -1
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { tone.release() } catch (_: Exception) { }
    }
}
