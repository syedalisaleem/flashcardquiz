package com.syedali.flashquiz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syedali.flashquiz.theme.Palette

@Composable
fun FlashcardFace(
    front: String,
    back: String,
    isFlipped: Boolean,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList()
) {
    val backgroundColor = if (isFlipped) {
        Palette.LightSurfaceElevated
    } else {
        Palette.LightSurface
    }
    val content = if (isFlipped) back.ifBlank { front } else front.ifBlank { back }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (isFlipped) Palette.LightPrimaryAccent else Palette.LightBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.LightPrimaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.LightMutedText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun QuizOptionChip(
    label: String,
    index: Int,
    isSelected: Boolean,
    isCorrect: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background: Color = when {
        isCorrect == true -> Palette.LightPrimaryAccent
        isSelected && isCorrect == false -> Palette.LightMutedText
        isSelected -> Palette.LightSurfaceElevated
        else -> Palette.LightSurface
    }
    val textColor = when {
        isCorrect == true -> Palette.LightPrimaryText
        isSelected && isCorrect == false -> Palette.LightSurfaceElevated
        else -> Palette.LightPrimaryText
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Palette.LightPrimaryAccent else Palette.LightBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "${'A' + index}.  $label",
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}
