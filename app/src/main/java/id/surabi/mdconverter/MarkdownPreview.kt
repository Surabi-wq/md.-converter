package id.surabi.mdconverter

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PaperInk = Color(0xFF26332E)
private val PaperGreen = Color(0xFF315F55)
private val PaperMuted = Color(0xFF718078)
private val PaperLine = Color(0xFFE2E9E5)

@Composable
internal fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    documentName: String? = null,
    sourceFormat: String? = null,
    warnings: List<String> = emptyList()
) {
    val blocks = remember(markdown) { MarkdownBlockParser.parse(markdown) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEAF0ED)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (warnings.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF7E8),
                    border = BorderStroke(1.dp, Color(0xFFF0DDB7))
                ) {
                    Column(
                        modifier = Modifier.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "CATATAN KONVERSI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF8C5B15)
                        )
                        warnings.forEach { warning ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", color = Color(0xFF9A681E), modifier = Modifier.padding(end = 7.dp))
                                Text(
                                    warning,
                                    color = Color(0xFF684D2A),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                shape = RoundedCornerShape(5.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFDCE4DF)),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 23.dp, vertical = 25.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "MARKA",
                            color = PaperGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.8.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            sourceFormat?.takeIf { it.isNotBlank() }?.uppercase() ?: "MARKDOWN",
                            color = PaperMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }
                    if (!documentName.isNullOrBlank()) {
                        Text(
                            documentName,
                            color = PaperMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    HorizontalDivider(color = PaperLine, thickness = 1.dp)

                    if (blocks.isEmpty()) {
                        Text(
                            "Tidak ada konten yang dapat ditampilkan.",
                            color = PaperMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        blocks.forEach { block ->
                            when (block) {
                                is MarkdownBlock.Heading -> {
                                    val headingSize = when (block.level) {
                                        1 -> 24.sp
                                        2 -> 20.sp
                                        3 -> 17.sp
                                        4 -> 15.sp
                                        else -> 14.sp
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            inlineMarkdown(block.text),
                                            fontSize = headingSize,
                                            lineHeight = (headingSize.value * 1.25f).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (block.level <= 2) Color(0xFF203F37) else PaperInk
                                        )
                                        if (block.level == 1) {
                                            HorizontalDivider(
                                                color = Color(0xFFB8CDC3),
                                                thickness = 1.dp
                                            )
                                        }
                                    }
                                }

                                is MarkdownBlock.Paragraph -> {
                                    Text(
                                        inlineMarkdown(block.text),
                                        color = PaperInk,
                                        fontSize = 13.sp,
                                        lineHeight = 21.sp,
                                        textAlign = TextAlign.Start
                                    )
                                }

                                is MarkdownBlock.BulletList -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        block.items.forEach { item ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Text(
                                                    "•",
                                                    modifier = Modifier.padding(end = 9.dp),
                                                    color = PaperGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    inlineMarkdown(item),
                                                    color = PaperInk,
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                is MarkdownBlock.NumberedList -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        block.items.forEachIndexed { index, item ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Text(
                                                    (index + 1).toString() + ".",
                                                    modifier = Modifier.width(22.dp),
                                                    color = PaperGreen,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    inlineMarkdown(item),
                                                    color = PaperInk,
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                is MarkdownBlock.Code -> {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF3F6F4),
                                        border = BorderStroke(1.dp, Color(0xFFE3EAE6))
                                    ) {
                                        Column(
                                            Modifier.padding(11.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            if (block.language.isNotBlank()) {
                                                Text(
                                                    block.language.uppercase(),
                                                    fontSize = 9.sp,
                                                    color = PaperMuted,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                            }
                                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                                Text(
                                                    block.content,
                                                    fontSize = 11.sp,
                                                    lineHeight = 17.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF263A32)
                                                )
                                            }
                                        }
                                    }
                                }

                                is MarkdownBlock.Quote -> {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(7.dp),
                                        color = Color(0xFFF0F5F2)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(11.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(
                                                modifier = Modifier.width(3.dp).height(34.dp),
                                                color = Color(0xFF6D9B88),
                                                shape = RoundedCornerShape(3.dp)
                                            ) {}
                                            Text(
                                                inlineMarkdown(block.text),
                                                modifier = Modifier.padding(start = 10.dp),
                                                fontSize = 12.sp,
                                                lineHeight = 19.sp,
                                                color = Color(0xFF486057)
                                            )
                                        }
                                    }
                                }

                                is MarkdownBlock.Table -> {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(7.dp),
                                        color = Color(0xFFF7F9F8),
                                        border = BorderStroke(1.dp, PaperLine)
                                    ) {
                                        Column(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            block.rows.forEachIndexed { rowIndex, row ->
                                                Surface(
                                                    color = when {
                                                        rowIndex == 0 -> Color(0xFFE4EEE9)
                                                        rowIndex % 2 == 0 -> Color(0xFFFAFCFB)
                                                        else -> Color.White
                                                    }
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        row.forEach { cell ->
                                                            Text(
                                                                inlineMarkdown(cell),
                                                                modifier = Modifier
                                                                    .widthIn(min = 104.dp)
                                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                                color = if (rowIndex == 0) PaperGreen else PaperInk,
                                                                fontSize = 11.sp,
                                                                lineHeight = 16.sp,
                                                                fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                MarkdownBlock.Rule -> {
                                    HorizontalDivider(color = PaperLine, thickness = 1.dp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = PaperLine, thickness = 1.dp)
                    Text(
                        "DOKUMEN HASIL KONVERSI • MARKA",
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF97A39D),
                        fontSize = 8.sp,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun inlineMarkdown(value: String) = buildAnnotatedString {
    val codeDelimiter = 96.toChar()
    val pattern = Regex(
        "(!?\\[[^]]+\\]\\([^)]+\\)|" +
            codeDelimiter + "[^" + codeDelimiter + "]+" + codeDelimiter +
            "|\\*\\*[^*]+\\*\\*|~~[^~]+~~|\\*[^*]+\\*)"
    )
    var cursor = 0
    pattern.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("![") -> {
                val label = token.substring(2).substringBefore("]")
                pushStyle(SpanStyle(color = Color(0xFF537D6E), fontWeight = FontWeight.Medium))
                append("▧ " + label)
                pop()
            }
            token.firstOrNull() == codeDelimiter -> {
                pushStyle(
                    SpanStyle(
                        background = Color(0xFFEAF0ED),
                        color = Color(0xFF315F55),
                        fontFamily = FontFamily.Monospace
                    )
                )
                append(token.substring(1, token.length - 1))
                pop()
            }
            token.startsWith("**") && token.endsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF203F37)))
                append(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("~~") && token.endsWith("~~") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("*") && token.endsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(token.substring(1, token.length - 1))
                pop()
            }
            token.startsWith("[") -> {
                val label = token.substringAfter("[").substringBefore("]")
                pushStyle(SpanStyle(color = PaperGreen, textDecoration = TextDecoration.Underline))
                append(label)
                pop()
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < value.length) append(value.substring(cursor))
}
