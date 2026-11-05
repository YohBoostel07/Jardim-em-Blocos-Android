package com.yohboostel.jardim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
    private data class Spark(val x: Float, val y: Float, val color: Int, val size: Float)

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
    private var showGardens = false
    private var gardenDetailOpen = false
    private var selectedGarden = 0
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
            if (showAchievements) drawAchievementsScreen(canvas, w, h) else drawMainMenu(canvas, w, h)
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
        if (showTutorial) drawTutorial(canvas, w, h)
        else if (confirmRestart) drawRestartConfirmation(canvas, w, h)
        else if (paused) drawPauseMenu(canvas, w, h)
        else if (gameOver) drawGameOver(canvas, w, h)
    }

    private fun drawMainMenu(c: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.rgb(17,55,48), Color.rgb(31,105,72), Color.rgb(112,157,91)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint); paint.shader = null
        drawGardenBackdrop(c,w,h)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded",Typeface.BOLD)
        paint.color = Color.WHITE; paint.textSize = w*.085f
        c.drawText("Jardim em",w/2f,h*.105f,paint); c.drawText("Blocos",w/2f,h*.19f,paint)
        paint.textSize=w*.029f; paint.color=Color.rgb(231,245,216); paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        c.drawText("UM MUNDO PARA CULTIVAR",w/2f,h*.225f,paint)
        val hero=RectF(w*.12f,h*.275f,w*.88f,h*.565f); drawGardenMiniature(c,0,gardenStages[0],hero)
        paint.color=Color.argb(45,13,46,31); c.drawRoundRect(hero,22f,22f,paint)
        paint.textSize=w*.028f; paint.color=Color.WHITE
        c.drawText("Recupere jardins e descubra novos lugares",w/2f,h*.60f,paint)
        val play=RectF(w*.15f,h*.65f,w*.85f,h*.735f); rounded(c,play,22f,Color.rgb(255,205,91))
        paint.color=Color.rgb(57,82,48); paint.textSize=w*.043f; paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        c.drawText("JOGAR",play.centerX(),play.centerY()+w*.015f,paint)
        val gardens=RectF(w*.15f,h*.755f,w*.47f,h*.835f); val achievements=RectF(w*.53f,h*.755f,w*.85f,h*.835f)
        rounded(c,gardens,18f,Color.rgb(239,231,197)); rounded(c,achievements,18f,Color.rgb(239,231,197))
        paint.color=Color.rgb(47,91,64); paint.textSize=w*.027f
        c.drawText("MEUS JARDINS",gardens.centerX(),gardens.centerY()+w*.01f,paint)
        c.drawText("CONQUISTAS",achievements.centerX(),achievements.centerY()+w*.01f,paint)
        paint.color=Color.rgb(255,232,166); paint.textSize=w*.031f
        c.drawText("✦  $coins moedas",w/2f,h*.895f,paint)
        paint.color=Color.argb(210,255,255,255); paint.textSize=w*.023f; paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        c.drawText("RECORDE  $best     •     NÍVEL " + (1+best/500),w/2f,h*.935f,paint)
    }

    private fun drawAchievementsScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h); rounded(c,RectF(w*.055f,h*.07f,w*.945f,h*.93f),26f,Color.rgb(239,231,197))
        paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64); paint.textSize=w*.055f; c.drawText("Conquistas",w/2f,h*.145f,paint)
        paint.textSize=w*.026f; paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL); paint.color=Color.rgb(103,111,87)
        c.drawText("Complete desafios e ganhe moedas",w/2f,h*.185f,paint)
        val titles=listOf("Primeira partida","Jardineiro dedicado","Pontuação de ouro","Combo espetacular")
        val details=listOf("Jogue sua primeira partida","Limpe 10 linhas no total","Alcance 500 pontos","Limpe 3 linhas de uma vez")
        val unlocked=listOf(gamesPlayed>=1,totalLines>=10,best>=500,bestCombo>=3)
        val keys=listOf("ach_first","ach_lines","ach_score","ach_combo"); val rewards=intArrayOf(50,75,100,100)
        for(i in 0..3) {
            val top=h*(.235f+i*.137f); val card=RectF(w*.10f,top,w*.90f,top+h*.115f)
            rounded(c,card,15f,if(unlocked[i]) Color.rgb(255,250,229) else Color.rgb(218,216,196))
            paint.color=if(unlocked[i]) Color.rgb(225,170,67) else Color.rgb(153,157,139); c.drawCircle(w*.19f,top+h*.057f,w*.045f,paint)
            paint.color=Color.WHITE; paint.textSize=w*.04f; c.drawText(if(unlocked[i]) "★" else "·",w*.19f,top+h*.071f,paint)
            paint.textAlign=Paint.Align.LEFT; paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
            paint.color=Color.rgb(47,91,64); paint.textSize=w*.030f; c.drawText(titles[i],w*.27f,top+h*.045f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL); paint.textSize=w*.022f; paint.color=Color.rgb(103,111,87)
            c.drawText(details[i],w*.27f,top+h*.078f,paint)
            val claimed=prefs.getBoolean(keys[i],false); val reward=RectF(w*.68f,top+h*.027f,w*.87f,top+h*.088f)
            rounded(c,reward,10f,if(unlocked[i]&&!claimed) Color.rgb(83,151,91) else Color.rgb(188,199,172))
            paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            paint.color=if(unlocked[i]&&!claimed) Color.WHITE else Color.rgb(76,91,66); paint.textSize=w*.021f
            c.drawText(if(claimed) "RESGATADA" else if(unlocked[i]) "+"+rewards[i] else "BLOQUEADA",reward.centerX(),reward.centerY()+w*.007f,paint)
        }
        val back=RectF(w*.22f,h*.82f,w*.78f,h*.89f); rounded(c,back,16f,Color.rgb(71,145,91))
        paint.color=Color.WHITE; paint.textSize=w*.029f; c.drawText("VOLTAR AO MENU",back.centerX(),back.centerY()+w*.01f,paint)
    }

    private fun drawGardenBackdrop(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(24, 255, 255, 255)
        for (i in 0..6) c.drawCircle(w * (i / 6f), h * .19f, w * .11f, paint)
        paint.color = Color.rgb(249, 218, 139)
        c.drawCircle(w * .86f, h * .115f, w * .043f, paint)
        paint.color = Color.argb(45, 255, 255, 255)
        c.drawOval(w * .06f, h * .12f, w * .28f, h * .17f, paint)
        c.drawOval(w * .20f, h * .105f, w * .42f, h * .16f, paint)
    }

    private fun drawHeader(c: Canvas, w: Float, h: Float) {
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.WHITE
        paint.textSize = w * .068f
        c.drawText("Jardim em Blocos", w / 2f, h * .071f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = Color.rgb(203, 235, 211)
        paint.textSize = w * .025f
        c.drawText("CULTIVE  •  COMBINE  •  FLORESÇA", w / 2f, h * .105f, paint)

        drawGardenShortcut(c, w, h)
        drawStat(c, w * .25f, h * .165f, "PONTOS", score.toString())
        drawStat(c, w * .69f, h * .165f, "RECORDE", max(best, score).toString())
        paint.color = Color.rgb(255, 226, 160)
        paint.textSize = w * .026f
        c.drawText("NÍVEL $level", w * .47f, h * .177f, paint)
        val button = RectF(w * .855f, h * .125f, w * .965f, h * .205f)
        rounded(c, button, 18f, Color.argb(150, 5, 35, 29))
        paint.color = Color.WHITE
        paint.textSize = w * .043f
        c.drawText("Ⅱ", button.centerX(), button.centerY() + w * .014f, paint)
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

    private fun drawGardenMiniature(c: Canvas, index: Int, stage: Int, rect: RectF) {
        rounded(c, rect, 13f, when (index) {
            0 -> Color.rgb(205, 229, 188)
            1 -> Color.rgb(190, 218, 184)
            else -> Color.rgb(184, 218, 218)
        })
        val ground = RectF(rect.left, rect.top + rect.height() * .67f, rect.right, rect.bottom)
        rounded(c, ground, 10f, when (index) {
            2 -> Color.rgb(105, 166, 151)
            else -> Color.rgb(133, 157, 91)
        })
        paint.color = Color.argb(90, 255, 255, 255)
        c.drawCircle(rect.left + rect.width() * .25f, rect.top + rect.height() * .24f, rect.width() * .10f, paint)
        c.drawCircle(rect.left + rect.width() * .34f, rect.top + rect.height() * .22f, rect.width() * .075f, paint)

        if (index == 2) {
            paint.color = Color.rgb(77, 160, 177)
            c.drawOval(rect.left + rect.width() * .12f, rect.top + rect.height() * .57f,
                rect.right - rect.width() * .10f, rect.bottom - rect.height() * .08f, paint)
            paint.color = Color.argb(120, 255, 255, 255)
            c.drawOval(rect.left + rect.width() * .25f, rect.top + rect.height() * .64f,
                rect.left + rect.width() * .58f, rect.top + rect.height() * .72f, paint)
        }

        if (stage >= 1) {
            val plantX = rect.left + rect.width() * .50f
            val baseY = rect.top + rect.height() * .78f
            paint.color = Color.rgb(55, 117, 64)
            paint.strokeWidth = max(2f, rect.width() * .025f)
            c.drawLine(plantX, baseY, plantX, rect.top + rect.height() * .40f, paint)
            c.drawOval(plantX - rect.width() * .18f, rect.top + rect.height() * .48f,
                plantX, rect.top + rect.height() * .61f, paint)
            c.drawOval(plantX, rect.top + rect.height() * .42f,
                plantX + rect.width() * .18f, rect.top + rect.height() * .56f, paint)
        }

        if (stage >= 2) {
            val treeX = rect.left + rect.width() * .76f
            val treeY = rect.top + rect.height() * .55f
            paint.color = Color.rgb(112, 78, 48)
            c.drawRect(treeX - rect.width() * .035f, treeY, treeX + rect.width() * .035f,
                rect.bottom - rect.height() * .13f, paint)
            paint.color = Color.rgb(58, 133, 76)
            c.drawCircle(treeX, treeY - rect.height() * .09f, rect.width() * .18f, paint)
            c.drawCircle(treeX - rect.width() * .10f, treeY, rect.width() * .13f, paint)
            c.drawCircle(treeX + rect.width() * .10f, treeY, rect.width() * .13f, paint)
        }

        if (stage >= 3) {
            val flowerColors = intArrayOf(Color.rgb(245, 118, 142), Color.rgb(255, 215, 94), Color.WHITE)
            for (i in 0..2) {
                val fx = rect.left + rect.width() * (.20f + i * .22f)
                val fy = rect.top + rect.height() * (.78f - (i % 2) * .08f)
                paint.color = flowerColors[i]
                c.drawCircle(fx, fy, rect.width() * .065f, paint)
                paint.color = Color.rgb(255, 224, 123)
                c.drawCircle(fx, fy, rect.width() * .025f, paint)
            }
        }
        paint.strokeWidth = 1f
    }

    private fun drawGardenDetailScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        rounded(c, RectF(w * .045f, h * .055f, w * .955f, h * .945f), 28f, Color.rgb(239, 231, 197))
        val index = selectedGarden.coerceIn(0, 2)
        val stage = gardenStages[index]
        val unlocked = index == 0 || gardenStages[index - 1] >= 3
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .052f
        c.drawText(gardenNames[index], w / 2f, h * .125f, paint)
        paint.textSize = w * .027f
        paint.color = Color.rgb(174, 116, 43)
        c.drawText("MOEDAS " + coins, w / 2f, h * .17f, paint)
        val scene = RectF(w * .10f, h * .205f, w * .90f, h * .57f)
        drawGardenMiniature(c, index, stage, scene)
        paint.textSize = w * .034f
        paint.color = Color.rgb(47, 91, 64)
        c.drawText("ETAPA " + stage + " DE 3", w / 2f, h * .625f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .027f
        paint.color = Color.rgb(103, 111, 87)
        c.drawText(gardenDescriptions[index], w / 2f, h * .67f, paint)
        val progress = RectF(w * .18f, h * .705f, w * .82f, h * .725f)
        rounded(c, progress, 10f, Color.rgb(218, 220, 195))
        if (stage > 0) rounded(c, RectF(progress.left, progress.top, progress.left + progress.width() * stage / 3f, progress.bottom), 10f, Color.rgb(91, 161, 99))
        val action = RectF(w * .18f, h * .755f, w * .82f, h * .835f)
        val canRestore = unlocked && stage < 3 && coins >= gardenCosts[index][stage]
        val label = when { !unlocked -> "JARDIM BLOQUEADO"; stage >= 3 -> "JARDIM RESTAURADO!"; else -> "RESTAURAR  •  " + gardenCosts[index][stage] + " MOEDAS" }
        rounded(c, action, 18f, if (canRestore) Color.rgb(71, 145, 91) else Color.rgb(178, 190, 165))
        paint.color = if (canRestore) Color.WHITE else Color.rgb(76, 91, 66)
        paint.textSize = w * .028f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        c.drawText(label, action.centerX(), action.centerY() + w * .01f, paint)
        val back = RectF(w * .25f, h * .865f, w * .75f, h * .92f)
        rounded(c, back, 15f, Color.rgb(71, 145, 91))
        paint.color = Color.WHITE
        paint.textSize = w * .029f
        c.drawText("VOLTAR AOS JARDINS", back.centerX(), back.centerY() + w * .01f, paint)
    }
    private fun drawGardensScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        rounded(c, RectF(w * .045f, h * .075f, w * .955f, h * .94f), 28f, Color.rgb(239, 231, 197))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .058f
        c.drawText("Seus jardins", w / 2f, h * .145f, paint)
        paint.textSize = w * .031f
        paint.color = Color.rgb(174, 116, 43)
        c.drawText("MOEDAS  $coins", w / 2f, h * .19f, paint)

        for (i in 0..2) {
            val top = h * (.225f + i * .174f)
            val bottom = top + h * .155f
            val unlocked = i == 0 || gardenStages[i - 1] >= 3
            val stage = gardenStages[i]
            val card = RectF(w * .085f, top, w * .915f, bottom)
            rounded(c, card, 18f, if (unlocked) Color.rgb(255, 250, 229) else Color.rgb(210, 207, 185))
            val scene = RectF(w * .105f, top + h * .018f, w * .275f, bottom - h * .018f)
            drawGardenMiniature(c, i, stage, scene)
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            paint.color = Color.rgb(47, 91, 64)
            paint.textSize = w * .034f
            c.drawText(gardenNames[i], w * .30f, top + h * .043f, paint)
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = w * .022f
            paint.color = Color.rgb(103, 111, 87)
            c.drawText(if (unlocked) gardenDescriptions[i] else "Conclua o jardim anterior para liberar.", w * .30f, top + h * .078f, paint)
            val progressRect = RectF(w * .30f, top + h * .103f, w * .57f, top + h * .119f)
            rounded(c, progressRect, 8f, Color.rgb(218, 220, 195))
            if (stage > 0) {
                val fill = RectF(progressRect.left, progressRect.top, progressRect.left + progressRect.width() * stage / 3f, progressRect.bottom)
                rounded(c, fill, 8f, Color.rgb(91, 161, 99))
            }
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.rgb(85, 104, 75)
            paint.textSize = w * .023f
            c.drawText("$stage/3 etapas", w * .30f, top + h * .145f, paint)
            val action = RectF(w * .62f, top + h * .045f, w * .87f, top + h * .112f)
            val label = when {
                !unlocked -> "BLOQUEADO"
                stage >= 3 -> "CONCLUÍDO"
                else -> "✦ ${gardenCosts[i][stage]}"
            }
            rounded(c, action, 13f, if (!unlocked || stage >= 3) Color.rgb(184, 194, 169) else Color.rgb(83, 151, 91))
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.color = if (!unlocked || stage >= 3) Color.rgb(76, 91, 66) else Color.WHITE
            paint.textSize = w * .026f
            c.drawText(label, action.centerX(), action.centerY() + w * .009f, paint)
        }

        val missionTop = h * .755f
        rounded(c, RectF(w * .085f, missionTop, w * .915f, h * .875f), 16f, Color.rgb(224, 239, 211))
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .027f
        c.drawText("MISSÃO DIÁRIA", w * .13f, h * .785f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .023f
        c.drawText("Limpe 5 linhas  •  $dailyLines/5", w * .13f, h * .812f, paint)
        val dailyButton = RectF(w * .65f, h * .765f, w * .87f, h * .817f)
        val canClaimDaily = dailyLines >= 5 && !dailyRewardClaimed
        rounded(c, dailyButton, 11f, if (canClaimDaily) Color.rgb(83, 151, 91) else Color.rgb(188, 199, 172))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = if (canClaimDaily) Color.WHITE else Color.rgb(76, 91, 66)
        paint.textSize = w * .021f
        c.drawText(if (dailyRewardClaimed) "RESGATADA" else if (canClaimDaily) "+100 MOEDAS" else "100 MOEDAS", dailyButton.centerX(), dailyButton.centerY() + w * .007f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .027f
        c.drawText("MISSÃO SEMANAL", w * .13f, h * .842f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .023f
        c.drawText("Limpe 20 linhas  •  $weeklyLines/20", w * .13f, h * .868f, paint)
        val weeklyButton = RectF(w * .65f, h * .825f, w * .87f, h * .877f)
        val canClaimWeekly = weeklyLines >= 20 && !weeklyRewardClaimed
        rounded(c, weeklyButton, 11f, if (canClaimWeekly) Color.rgb(83, 151, 91) else Color.rgb(188, 199, 172))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = if (canClaimWeekly) Color.WHITE else Color.rgb(76, 91, 66)
        paint.textSize = w * .021f
        c.drawText(if (weeklyRewardClaimed) "RESGATADA" else if (canClaimWeekly) "+250 MOEDAS" else "250 MOEDAS", weeklyButton.centerX(), weeklyButton.centerY() + w * .007f, paint)

        val back = RectF(w * .25f, h * .885f, w * .75f, h * .925f)
        rounded(c, back, 14f, Color.rgb(71, 145, 91))
        paint.color = Color.WHITE
        paint.textSize = w * .029f
        c.drawText("VOLTAR AO JOGO", back.centerX(), back.centerY() + w * .009f, paint)
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
        val box = RectF(x - width * .17f, y - height * .026f, x + width * .17f, y + height * .047f)
        rounded(c, box, height * .018f, Color.argb(105, 5, 35, 29))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = Color.rgb(193, 224, 200)
        paint.textSize = width * .023f
        c.drawText(label, x, y, paint)
        paint.color = Color.WHITE
        paint.textSize = width * .040f
        c.drawText(value, x, y + height * .039f, paint)
    }

    private fun drawBoard(c: Canvas) {
        val outer = RectF(boardLeft - 8f, boardTop - 8f, boardLeft + boardSize + 8f, boardTop + boardSize + 8f)
        rounded(c, outer, 24f, Color.argb(225, 8, 39, 33))
        for (r in 0..8) for (col in 0..8) {
            val left = boardLeft + col * cell
            val top = boardTop + r * cell
            val rect = RectF(left + 2f, top + 2f, left + cell - 2f, top + cell - 2f)
            rounded(c, rect, cell * .17f, if (board[r][col]) colors[r][col] else Color.argb(76, 207, 239, 215))
            if (board[r][col]) {
                paint.color = Color.argb(65, 255, 255, 255)
                c.drawCircle(left + cell * .28f, top + cell * .24f, cell * .085f, paint)
                paint.color = Color.argb(35, 0, 0, 0)
                c.drawRoundRect(rect.left + cell * .08f, rect.bottom - cell * .13f,
                    rect.right - cell * .08f, rect.bottom - cell * .04f, cell * .05f, cell * .05f, paint)
            }
        }
        if (dragging && previewRow >= 0 && previewCol >= 0) {
            val p = pieces.getOrNull(selected)
            if (p != null) p.cells.forEach { (dx, dy) ->
                val row = previewRow + dy
                val col = previewCol + dx
                if (row in 0..8 && col in 0..8) {
                    val rect = RectF(boardLeft + col * cell + 2f, boardTop + row * cell + 2f,
                        boardLeft + (col + 1) * cell - 2f, boardTop + (row + 1) * cell - 2f)
                    rounded(c, rect, cell * .17f,
                        if (previewValid) withAlpha(p.color, 155) else Color.argb(145, 235, 75, 75))
                }
            }
        }
    }

    private fun drawPieceTray(c: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(231, 246, 230)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = w * .030f
        c.drawText("ESCOLHA UMA PEÇA", w / 2f, slotsTop - h * .020f, paint)
        val slotWidth = w * .275f
        for (i in 0..2) {
            val centerX = w * (.18f + i * .32f)
            val active = i == selected && pieces[i] != null
            val rect = RectF(centerX - slotWidth / 2f, trayCenterY - h * .047f,
                centerX + slotWidth / 2f, trayCenterY + h * .047f)
            rounded(c, rect, 19f, when {
                pieces[i] == null -> Color.argb(45, 7, 38, 32)
                active -> Color.rgb(247, 231, 184)
                else -> Color.argb(105, 7, 38, 32)
            })
            if (active) {
                paint.color = Color.rgb(128, 94, 49)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                c.drawRoundRect(rect, 19f, 19f, paint)
                paint.style = Paint.Style.FILL
            }
            val piece = pieces[i]
            if (piece != null && !(dragging && i == selected)) {
                drawPiece(c, piece, centerX, trayCenterY - h * .004f, min(cell * .37f, h * .025f))
            } else if (piece == null) {
                paint.color = Color.argb(115, 235, 247, 232)
                paint.textSize = w * .045f
                c.drawText("✓", centerX, trayCenterY + h * .012f, paint)
            }
        }
        if (comboAlpha > 0f) {
            paint.color = withAlpha(Color.rgb(255, 226, 139), (comboAlpha * 255).toInt())
            paint.textSize = w * .043f
            paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            c.drawText(comboLabel, w / 2f, trayCenterY + h * .105f, paint)
        }
    }

    private fun drawFooter(c: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(218, 241, 220)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .025f
        c.drawText("Arraste uma peça para o tabuleiro", w / 2f, h * .955f, paint)
    }

    private fun drawPiece(c: Canvas, piece: Piece, cx: Float, cy: Float, size: Float) {
        val maxX = piece.cells.maxOf { it.first } + 1
        val maxY = piece.cells.maxOf { it.second } + 1
        val unit = min(size * 2.35f / maxX, size * 2.35f / maxY)
        piece.cells.forEach { (x, y) ->
            val left = cx - maxX * unit / 2f + x * unit
            val top = cy - maxY * unit / 2f + y * unit
            val rect = RectF(left, top, left + unit - 3f, top + unit - 3f)
            rounded(c, rect, 7f, piece.color)
            paint.color = Color.argb(65, 255, 255, 255)
            c.drawCircle(rect.left + rect.width() * .28f, rect.top + rect.height() * .24f, unit * .09f, paint)
        }
    }

    private fun drawSparks(c: Canvas) {
        sparks.forEach {
            paint.color = withAlpha(it.color, (255 * (1f - effectProgress)).toInt().coerceIn(0, 255))
            c.drawCircle(it.x, it.y - effectProgress * height * .07f,
                it.size * (1f - effectProgress * .35f), paint)
        }
    }

    private fun drawDim(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(205, 4, 23, 19)
        c.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawPanel(c: Canvas, w: Float, top: Float, bottom: Float) {
        rounded(c, RectF(w * .075f, top, w * .925f, bottom), 28f, Color.rgb(239, 231, 197))
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
        drawButton(c, w, h * .585f, "JOGAR NOVAMENTE", true)
    }

    private fun drawButton(c: Canvas, w: Float, centerY: Float, label: String, primary: Boolean) {
        val rect = RectF(w * .20f, centerY - height * .027f, w * .80f, centerY + height * .027f)
        rounded(c, rect, 18f, if (primary) Color.rgb(71, 145, 91) else Color.rgb(194, 211, 177))
        paint.color = if (primary) Color.WHITE else Color.rgb(47, 91, 64)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = w * .031f
        c.drawText(label, rect.centerX(), rect.centerY() + w * .011f, paint)
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
                sparks = sparks + Spark(boardLeft + (c + .5f) * cell,
                    boardTop + (r + .5f) * cell, colors[r][c], cell * .17f)
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
            playTone(if (lines >= 3) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 180)
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
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
        gameOver = !availablePiecesHaveMove()
    }

    private fun animateEffects() {
        effectProgress = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 480
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                effectProgress = it.animatedValue as Float
                comboAlpha = 1f - effectProgress
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
            if (mainMenuOpen || showTutorial || paused || gameOver || confirmRestart || showGardens) return true
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
                if (dragging) {
                    updatePreview(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (mainMenuOpen) {
                    if (showAchievements) {
                        if (event.y > height*.80f) showAchievements=false
                        else {
                            val idx=((event.y/height-.235f)/.137f).toInt()
                            if(idx in 0..3) {
                                val unlocked=when(idx){0->gamesPlayed>=1;1->totalLines>=10;2->best>=500;else->bestCombo>=3}
                                val key=listOf("ach_first","ach_lines","ach_score","ach_combo")[idx]
                                val reward=intArrayOf(50,75,100,100)[idx]
                                if(unlocked&&!prefs.getBoolean(key,false)){coins+=reward;prefs.edit().putBoolean(key,true).apply();saveGardenProgress();playTone(ToneGenerator.TONE_PROP_ACK,160)}
                            }
                        }
                    } else {
                        when {
                            event.y in height*.64f..height*.745f -> {mainMenuOpen=false;if(showTutorial)finishTutorial()}
                            event.y in height*.75f..height*.85f && event.x<width*.50f -> {mainMenuOpen=false;showGardens=true}
                            event.y in height*.75f..height*.85f && event.x>=width*.50f -> showAchievements=true
                        }
                    }
                    invalidate(); return true
                }
                if (showTutorial) {
                    finishTutorial()
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
                            event.y in height * .875f..height * .94f -> showGardens = false
                            event.y in height * .755f..height * .82f && event.x > width * .62f && dailyLines >= 5 && !dailyRewardClaimed -> {
                                dailyRewardClaimed = true
                                coins += 100
                                saveGardenProgress()
                                playTone(ToneGenerator.TONE_PROP_ACK, 150)
                            }
                            event.y in height * .82f..height * .88f && event.x > width * .62f && weeklyLines >= 20 && !weeklyRewardClaimed -> {
                                weeklyRewardClaimed = true
                                coins += 250
                                saveGardenProgress()
                                playTone(ToneGenerator.TONE_PROP_ACK, 180)
                            }
                            event.y in height * .225f..height * .38f -> { selectedGarden = 0; if (event.x < width * .58f) gardenDetailOpen = true else restoreGarden(0) }
                            event.y in height * .399f..height * .554f -> { selectedGarden = 1; if (event.x < width * .58f) gardenDetailOpen = true else restoreGarden(1) }
                            event.y in height * .573f..height * .728f -> { selectedGarden = 2; if (event.x < width * .58f) gardenDetailOpen = true else restoreGarden(2) }
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
