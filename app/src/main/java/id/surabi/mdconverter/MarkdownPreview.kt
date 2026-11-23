package id.surabi.mdconverter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
    val insights = remember(markdown, blocks) { MarkdownDocumentInsightsCalculator.calculate(blocks) }
    val scrollState = rememberScrollState()
    val headingPositions = remember(markdown) { mutableStateMapOf<Int, Int>() }
    val viewportTop = remember(markdown) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val searchExpanded = remember(markdown) { mutableStateOf(false) }
    val searchQuery = remember(markdown) { mutableStateOf("") }
    val selectedSearchMatch = remember(markdown) { mutableStateOf(0) }
    val searchMatches = remember(markdown, searchQuery.value, blocks) {
        val query = searchQuery.value.orEmpty().trim()
        if (query.isBlank()) emptyList()
        else blocks.mapIndexedNotNull { index, block ->
            index.takeIf { block.searchableText().contains(query, ignoreCase = true) }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEAF0ED)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .onGloballyPositioned { viewportTop.value = it.positionInRoot().y.toInt() }
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

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { searchExpanded.value = !searchExpanded.value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (searchExpanded.value) "TUTUP PENCARIAN" else "CARI DI DOKUMEN",
                            color = PaperGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (searchExpanded.value) "−" else "⌕",
                            color = PaperGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (searchExpanded.value) {
                        OutlinedTextField(
                            value = searchQuery.value,
                            onValueChange = {
                                searchQuery.value = it
                                selectedSearchMatch.value = 0
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Cari kata atau frasa") },
                            placeholder = { Text("Contoh: kesimpulan, total, tanggal") }
                        )
                        if (searchQuery.value.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (searchMatches.isEmpty()) "Tidak ditemukan"
                                    else "Hasil ${selectedSearchMatch.value + 1} dari ${searchMatches.size}",
                                    color = PaperMuted,
                                    fontSize = 10.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "‹ Sebelumnya",
                                    modifier = Modifier.clickable {
                                        if (searchMatches.isNotEmpty()) {
                                            val next = (selectedSearchMatch.value - 1 + searchMatches.size) % searchMatches.size
                                            selectedSearchMatch.value = next
                                            headingPositions[searchMatches[next]]?.let { target ->
                                                scope.launch { scrollState.animateScrollTo(target.coerceAtLeast(0)) }
                                            }
                                        }
                                    }.padding(6.dp),
                                    color = PaperGreen,
                                    fontSize = 10.sp
                                )
                                Text(
                                    "Berikutnya ›",
                                    modifier = Modifier.clickable {
                                        if (searchMatches.isNotEmpty()) {
                                            val next = (selectedSearchMatch.value + 1) % searchMatches.size
                                            selectedSearchMatch.value = next
                                            headingPositions[searchMatches[next]]?.let { target ->
                                                scope.launch { scrollState.animateScrollTo(target.coerceAtLeast(0)) }
                                            }
                                        }
                                    }.padding(6.dp),
                                    color = PaperGreen,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF5F8F6),
                        border = BorderStroke(1.dp, PaperLine)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RINGKASAN DOKUMEN", color = PaperGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InsightMetric(insights.wordCount.toString(), "KATA", Modifier.weight(1f))
                                InsightMetric(insights.sectionCount.toString(), "BAGIAN", Modifier.weight(1f))
                                InsightMetric(insights.tableCount.toString(), "TABEL", Modifier.weight(1f))
                                InsightMetric(insights.readingMinutes.toString() + " mnt", "BACA", Modifier.weight(1f))
                            }
                        }
                    }

                    val outline = blocks.mapIndexedNotNull { index, block ->
                        (block as? MarkdownBlock.Heading)?.let { index to it }
                    }
                    if (outline.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFAFCFB),
                            border = BorderStroke(1.dp, PaperLine)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("DAFTAR ISI", color = PaperGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                outline.take(20).forEach { (index, heading) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val target = headingPositions[index]
                                                if (target != null) scope.launch { scrollState.animateScrollTo(target.coerceAtLeast(0)) }
                                            }
                                            .padding(start = ((heading.level - 1).coerceAtMost(5) * 10).dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("›", color = PaperGreen, fontSize = 13.sp, modifier = Modifier.padding(end = 7.dp))
                                        Text(heading.text, color = PaperInk, fontSize = 11.sp, maxLines = 2, lineHeight = 15.sp)
                                    }
                                }
                                if (outline.size > 20) Text("+ " + (outline.size - 20) + " bagian lainnya", color = PaperMuted, fontSize = 10.sp)
                            }
                        }
                    }

                    if (blocks.isEmpty()) {
                        Text(
                            "Tidak ada konten yang dapat ditampilkan.",
                            color = PaperMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        blocks.forEachIndexed { blockIndex, block ->
                            Column(
                                modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                                    headingPositions[blockIndex] = (
                                        scrollState.value + coordinates.positionInRoot().y.toInt() - viewportTop.value
                                    ).coerceAtLeast(0)
                                }
                            ) {
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
                                            inlineMarkdown(block.text, searchQuery.value),
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
                                        inlineMarkdown(block.text, searchQuery.value),
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
                                                    inlineMarkdown(item, searchQuery.value),
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
                                                    inlineMarkdown(item, searchQuery.value),
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
                                                inlineMarkdown(block.text, searchQuery.value),
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
                                                                inlineMarkdown(cell, searchQuery.value),
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

private fun inlineMarkdown(value: String, searchQuery: String = "") = buildAnnotatedString {
    fun appendHighlighted(text: String) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            append(text)
            return
        }
        var cursor = 0
        while (cursor < text.length) {
            val matchStart = text.indexOf(query, cursor, ignoreCase = true)
            if (matchStart < 0) {
                append(text.substring(cursor))
                break
            }
            if (matchStart > cursor) append(text.substring(cursor, matchStart))
            val matchEnd = matchStart + query.length
            pushStyle(
                SpanStyle(
                    background = Color(0xFFFFE39A),
                    color = Color(0xFF26332E),
                    fontWeight = FontWeight.Bold
                )
            )
            append(text.substring(matchStart, matchEnd))
            pop()
            cursor = matchEnd
        }
    }

    val codeDelimiter = 96.toChar()
    val pattern = Regex(
        "(!?\\\\[[^]]+\\\\]\\\\([^)]+\\\\)|" +
            codeDelimiter + "[^" + codeDelimiter + "]+" + codeDelimiter +
            "|\\\\*\\\\*[^*]+\\\\*\\\\*|~~[^~]+~~|\\\\*[^*]+\\\\*)"
    )
    var cursor = 0
    pattern.findAll(value).forEach { match ->
        appendHighlighted(value.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("![") -> {
                val label = token.substring(2).substringBefore("]")
                pushStyle(SpanStyle(color = Color(0xFF537D6E), fontWeight = FontWeight.Medium))
                appendHighlighted("▧ " + label)
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
                appendHighlighted(token.substring(1, token.length - 1))
                pop()
            }
            token.startsWith("**") && token.endsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF203F37)))
                appendHighlighted(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("~~") && token.endsWith("~~") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                appendHighlighted(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("*") && token.endsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendHighlighted(token.substring(1, token.length - 1))
                pop()
            }
            token.startsWith("[") -> {
                val label = token.substringAfter("[").substringBefore("]")
                pushStyle(SpanStyle(color = PaperGreen, textDecoration = TextDecoration.Underline))
                appendHighlighted(label)
                pop()
            }
            else -> appendHighlighted(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < value.length) appendHighlighted(value.substring(cursor))
}


@Composable
private fun InsightMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = PaperInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = PaperMuted, fontSize = 8.sp, letterSpacing = 0.6.sp, maxLines = 1)
    }
}


private fun MarkdownBlock.searchableText(): String = when (this) {
    is MarkdownBlock.Heading -> text
    is MarkdownBlock.Paragraph -> text
    is MarkdownBlock.BulletList -> items.joinToString(" ")
    is MarkdownBlock.NumberedList -> items.joinToString(" ")
    is MarkdownBlock.Code -> content
    is MarkdownBlock.Quote -> text
    is MarkdownBlock.Table -> rows.flatten().joinToString(" ")
    MarkdownBlock.Rule -> ""
}
