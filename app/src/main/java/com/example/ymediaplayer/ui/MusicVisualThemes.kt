package com.example.ymediaplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ymediaplayer.data.MusicItem
import com.example.ymediaplayer.theme.LocalAppColors
import kotlin.math.*

/** Read animation state in drawing/layer blocks, keeping per-frame work out of composition. */
@Composable
internal fun MusicVisualTheme(
    style: String, song: MusicItem, progress: Float,
    phase: () -> Float, spin: () -> Float, energy: () -> Float,
    bands: () -> FloatArray, waveform: () -> FloatArray
) {
    val c = LocalAppColors.current
    val accent = c.accentBlue
    val cyan = Color(0xFF56DDE5)
    val violet = Color(0xFFA69AFF)
    val ink = Color(0xFF101827)
    val foreground = if (c.isDark) Color.White else ink
    val rim = Brush.linearGradient(listOf(Color.White.copy(0.7f), accent.copy(0.2f), Color.White.copy(0.15f)))
    val frame = RoundedCornerShape(28.dp)
    Box(Modifier.size(320.dp, 290.dp), contentAlignment = Alignment.Center) {
        when (style) {
            "CASSETTE" -> {
                Column(Modifier.width(300.dp).height(198.dp)
                    .graphicsLayer { rotationZ = sin(phase() * 0.4f) * 0.5f }
                    .shadow(16.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF344052), Color(0xFF111723))))
                    .border(1.dp, rim, RoundedCornerShape(20.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Album(song, Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)))
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text("STUDIO / SIDE A", color = Color(0xFF92A4B9), fontSize = 8.sp, letterSpacing = 1.5.sp)
                            Text(song.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("C•90", color = Color(0xFFFFCE8C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
                        val radius = size.height * 0.38f
                        drawRoundRect(Color(0xFF080D15), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f,14f))
                        val centers = listOf(Offset(size.width * 0.25f, center.y), Offset(size.width * 0.75f, center.y))
                        drawLine(Color(0xFF88633E), centers[0], centers[1], radius * 0.5f)
                        centers.forEachIndexed { index, point ->
                            val fill = if (index == 0) 1f - progress * 0.45f else 0.55f + progress * 0.45f
                            drawCircle(Color(0xFF755239), radius * fill, point)
                            drawCircle(Color(0xFFB7A088), radius * fill, point, style = Stroke(1.dp.toPx()))
                            drawCircle(Color(0xFF263448), radius * 0.52f, point)
                            rotate(spin() * if (index == 0) 2f else 2.35f, point) {
                                repeat(6) { spoke ->
                                    rotate(spoke * 60f, point) {
                                        drawLine(Color(0xFFD8E1EB), point + Offset(radius * 0.21f,0f),
                                            point + Offset(radius * 0.44f,0f), 3.dp.toPx(), StrokeCap.Round)
                                    }
                                }
                            }
                            drawCircle(Color(0xFF070B10), radius * 0.16f, point)
                        }
                        repeat(4) { index ->
                            val p = Offset(if(index % 2 == 0) 9f else size.width - 9f,
                                if(index < 2) 9f else size.height - 9f)
                            drawCircle(Color(0xFF7F8A99), 2.dp.toPx(), p)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ANALOG • STEREO", color = Color(0xFFB7C4D3), fontSize = 8.sp, letterSpacing = 1.sp)
                        Canvas(Modifier.width(92.dp).height(10.dp)) {
                            phase() // Invalidate the meter when the shared animation clock advances.
                            val levels = bands()
                            repeat(18) { i ->
                                val value = levels.getOrElse(i * levels.size / 18) { 0f }.coerceIn(0f,1f)
                                drawRoundRect((if(i > 14) Color(0xFFFF9C78) else cyan).copy(alpha = 0.2f + value * 0.8f),
                                    Offset(i * size.width / 18, 0f), Size(size.width / 24, size.height))
                            }
                        }
                    }
                }
            }
            "CARD" -> {
                Column(Modifier.width(248.dp).height(278.dp)
                    .graphicsLayer {
                        rotationY = sin(phase() * 0.65f) * 3f
                        rotationZ = sin(phase() * 0.4f) * 0.6f
                        translationY = sin(phase() * 0.75f) * 3.dp.toPx()
                        cameraDistance = 20f * density
                    }.shadow(18.dp, frame).clip(frame)
                    .background((if(c.isDark) ink else Color.White).copy(0.65f))
                    .border(1.dp, rim, frame).padding(10.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))) {
                        Album(song, Modifier.fillMaxSize())
                        Canvas(Modifier.matchParentSize()) {
                            val shift = sin(phase() * 0.6f) * size.width * 0.4f
                            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(0.16f), Color.Transparent),
                                Offset(shift,0f), Offset(shift + size.width * 0.8f,size.height)))
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(32.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("STUDIO", color = foreground.copy(0.65f), letterSpacing = 2.sp, fontSize = 8.sp, modifier = Modifier.weight(1f))
                        MiniSpectrum(accent, bands, phase, Modifier.width(66.dp).height(18.dp))
                    }
                }
            }
            else -> {
                Canvas(Modifier.fillMaxSize()) {
                    val t = phase()
                    val e = energy().coerceIn(0f,1f)
                    val r = size.minDimension * 0.34f
                    val colors = listOf(accent, cyan, violet, accent)
                    drawCircle(Brush.radialGradient(listOf(accent.copy(0.16f + e * 0.08f), Color.Transparent)), size.minDimension * 0.5f)
                    when(style) {
                        "SONIC_REACTOR" -> {
                            val values = bands()
                            repeat(72) { i ->
                                val value = values.getOrElse(i * values.size / 72) { 0f }.coerceIn(0f,1f)
                                val a = i * 2f * PI.toFloat() / 72 - PI.toFloat()/2
                                val base = r * 0.89f
                                val length = r * (0.07f + value * 0.38f)
                                val direction = Offset(cos(a),sin(a))
                                drawLine(lerp(accent, cyan, i / 72f), center + direction * base,
                                    center + direction * (base + length), 2.dp.toPx(), StrokeCap.Round)
                            }
                            rotate(t * 12f) {
                                repeat(3) { i -> drawArc(colors[i].copy(0.55f), i*120f, 64f, false,
                                    center - Offset(r*0.81f,r*0.81f), Size(r*1.62f,r*1.62f), style = Stroke(1.dp.toPx())) }
                            }
                        }
                        "WAVE" -> {
                            val samples = waveform()
                            repeat(4) { layer ->
                                val path = Path()
                                repeat(121) { i ->
                                    val fraction = i / 120f
                                    val sample = samples.getOrElse(i * samples.size / 121) { 0f }.coerceIn(-1f,1f)
                                    val envelope = sin(fraction * PI.toFloat())
                                    val wave = sin(fraction * PI.toFloat() * (3f + layer * 0.4f) + t * (1f + layer * 0.14f))
                                    val y = size.height * (0.55f + layer * 0.035f) +
                                        envelope * (wave * r * (0.16f + e * 0.25f) + sample * r * 0.28f)
                                    if(i == 0) path.moveTo(0f,y) else path.lineTo(size.width * fraction,y)
                                }
                                val ribbon = Brush.horizontalGradient(
                                    0f to Color.Transparent, 0.13f to colors[layer].copy(0.65f),
                                    0.87f to colors[layer].copy(0.65f), 1f to Color.Transparent)
                                drawPath(path, ribbon, alpha = 0.16f, style = Stroke((12-layer*2).dp.toPx(), cap = StrokeCap.Round))
                                drawPath(path, ribbon, style = Stroke((1.8f-layer*0.25f).dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                        "ECLIPSE" -> {
                            val gold = Color(0xFFFFC780)
                            drawCircle(Brush.radialGradient(listOf(Color.Transparent, gold.copy(0.05f), gold.copy(0.28f), Color.Transparent)), r * 1.32f)
                            repeat(96) { i ->
                                val a = i * 2f * PI.toFloat()/96
                                val shimmer = 0.5f + 0.5f*sin(a*3f+t)
                                val direction = Offset(cos(a), sin(a))
                                drawLine(gold.copy(0.12f+shimmer*0.35f), center + direction*r*0.91f,
                                    center + direction*r*(0.96f+shimmer*0.18f+e*0.08f), 1.dp.toPx())
                            }
                            rotate(t*9f) {
                                drawOval(gold.copy(0.25f), center-Offset(r*1.35f,r*0.37f), Size(r*2.7f,r*0.74f), style=Stroke(1.dp.toPx()))
                                drawCircle(gold,3.dp.toPx(),center+Offset(r*1.35f,0f))
                            }
                        }
                        else -> { // Aurora Orb: flowing orbital bands around a stable artwork core.
                            repeat(5) { layer ->
                                rotate(t * (7f + layer*3f) + layer*34f) {
                                    val rx = r * (1.0f + layer*0.035f)
                                    val ry = r * (0.64f + layer*0.065f)
                                    drawOval(Brush.sweepGradient(colors.map { it.copy(0.45f) }),
                                        center-Offset(rx,ry),Size(rx*2,ry*2),style=Stroke((1.2f+layer*0.15f).dp.toPx()))
                                }
                            }
                        }
                    }
                    if(style != "WAVE") repeat(12) { i ->
                        val a = i*2f*PI.toFloat()/12+t*0.12f
                        val d = r*(1.25f+0.09f*sin(i.toFloat()+t))
                        drawCircle(colors[i%3].copy(0.22f), (0.7f+i%3*0.35f).dp.toPx(),center+Offset(cos(a)*d,sin(a)*d))
                    }
                }
                val artSize = when(style) { "WAVE" -> 174.dp; "SONIC_REACTOR" -> 154.dp; "ECLIPSE" -> 170.dp; else -> 156.dp }
                val shape = if(style == "WAVE") RoundedCornerShape(30.dp) else CircleShape
                Box(Modifier.size(artSize).graphicsLayer {
                    if(style == "WAVE") { translationY = -20.dp.toPx(); rotationZ = sin(phase()*0.5f)*1f }
                }.shadow(16.dp,shape).clip(shape).border(1.5.dp,rim,shape)) {
                    Album(song,Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Album(song: MusicItem, modifier: Modifier) {
    MusicAlbumArtImage(song.albumArtUri, modifier, seedText = song.title + song.artist)
}

@Composable
private fun MiniSpectrum(color: Color, bands: () -> FloatArray, clock: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        clock()
        val values = bands()
        repeat(16) { i ->
            val value = values.getOrElse(i * values.size / 16) { 0f }.coerceIn(0f,1f)
            val height = size.height * (0.12f + value * 0.88f)
            drawLine(color.copy(0.8f),Offset(i*size.width/16,center.y-height/2),
                Offset(i*size.width/16,center.y+height/2),2.dp.toPx(),StrokeCap.Round)
        }
    }
}

@Composable
internal fun VinylRecordHighlights(spin: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension/2
        repeat(44) { i ->
            drawCircle(Color.White.copy(if (i % 4 == 0) 0.075f else 0.032f),
                r * (0.53f + i * 0.0102f), style = Stroke(0.45.dp.toPx()))
        }
        drawCircle(Color(0xFF9DA9BA).copy(0.42f), r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
        rotate(spin()*0.08f) {
            drawArc(Brush.sweepGradient(listOf(Color.Transparent,Color.White.copy(0.16f),Color.Transparent,Color.White.copy(0.04f),Color.Transparent)),
                0f,360f,false, style=Stroke(1.5.dp.toPx()))
            drawArc(Color.White.copy(0.065f),218f,58f,false,Offset(r*0.08f,r*0.08f),Size(r*1.84f,r*1.84f),style=Stroke(r*0.25f))
        }
    }
}
