package id.surabi.mdconverter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownBlockParser.parse(markdown) }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = inlineMarkdown(block.text),
                        fontSize = when (block.level) {
                            1 -> 22.sp
                            2 -> 19.sp
                            3 -> 17.sp
                            4 -> 15.sp
                            else -> 14.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF203D36)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(inlineMarkdown(block.text), fontSize = 13.sp, lineHeight = 19.sp)
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        block.items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF315F55))
                                Text(inlineMarkdown(item), fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
                is MarkdownBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text((index + 1).toString() + ".", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF315F55))
                                Text(inlineMarkdown(item), fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
                is MarkdownBlock.Code -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFFF0F3F1)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (block.language.isNotBlank()) {
                                Text(block.language.uppercase(), fontSize = 10.sp, color = Color(0xFF587068), fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                Text(
                                    block.content,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF23312C)
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Quote -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEAF1ED)
                    ) {
                        Text(
                            inlineMarkdown(block.text),
                            modifier = Modifier.padding(11.dp),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF49635D)
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        block.rows.forEachIndexed { rowIndex, row ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                row.forEach { cell ->
                                    Surface(
                                        modifier = Modifier.widthIn(min = 96.dp),
                                        color = if (rowIndex == 0) Color(0xFFE3EEE9) else Color(0xFFF7F9F8)
                                    ) {
                                        Text(
                                            inlineMarkdown(cell),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                            fontSize = 12.sp,
                                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                MarkdownBlock.Rule -> {
                    HorizontalDivider(color = Color(0xFFD8E2DD))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

private fun inlineMarkdown(value: String) = buildAnnotatedString {
    val pattern = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|~~[^~]+~~|\\[[^]]+\\]\\([^)]+\\))")
    var cursor = 0
    pattern.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("~~") && token.endsWith("~~") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("*") && token.endsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                append(token.substring(1, token.length - 1))
                pop()
            }
            token.startsWith("[") -> {
                val label = token.substringAfter("[").substringBefore("]")
                pushStyle(SpanStyle(color = Color(0xFF315F55), textDecoration = TextDecoration.Underline))
                append(label)
                pop()
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < value.length) append(value.substring(cursor))
}
