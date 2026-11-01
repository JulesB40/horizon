package dev.horizonlab.issues.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizonlab.issues.ui.theme.GeistMono
import dev.horizonlab.issues.ui.theme.Palette
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.Type

/** The GitHub-flavoured Markdown issues use, enough of it to read them: no HTML beyond images and line breaks. */
private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Item(val marker: String, val text: String, val depth: Int, val checked: Boolean?) : Block
    data class Quote(val text: String) : Block
    data class Code(val text: String) : Block
    data class Image(val url: String, val alt: String) : Block
    data class Video(val url: String, val name: String) : Block
    data object Rule : Block
}

private val IMAGE_LINE = Regex("""^!\[([^\]]*)]\(([^)\s]+)[^)]*\)$""")
private val IMG_TAG = Regex("""<img\b[^>]*\bsrc=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val LIST_ITEM = Regex("""^(\s*)([-*+]|\d+[.)])\s+(\[[ xX]]\s+)?(.*)$""")
/** A line that is only a link, `[name](url)` or a bare URL: an attachment when the URL names a media file. */
private val LINK_ONLY = Regex("""^\[([^\]]+)]\((\S+?)(?:\s+"[^"]*")?\)$""")
private val URL_ONLY = Regex("""^<?(https?://\S+?)>?$""")

/** The media block for a line that is only a link to an image or a video, else null. */
private fun mediaBlock(text: String): Block? {
    val t = text.trim()
    val (name, url) = LINK_ONLY.find(t)?.let { it.groupValues[1] to it.groupValues[2] }
        ?: URL_ONLY.find(t)?.let { m -> m.groupValues[1].let { it.substringBefore('?').substringAfterLast('/') to it } }
        ?: return null
    return when (mediaKind(url)) {
        MediaKind.IMAGE -> Block.Image(url, name)
        MediaKind.VIDEO -> Block.Video(url, name)
        null -> null
    }
}

private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*$""")

private fun parse(markdown: String): List<Block> {
    val text = markdown
        .replace("\r\n", "\n")
        .replace(Regex("(?s)<!--.*?-->"), "")
        .replace(IMG_TAG) { "\n![image](${it.groupValues[1]})\n" }
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</?summary>", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("</?(details|p|div|sub|sup|kbd|picture|source|video)[^>]*>", RegexOption.IGNORE_CASE), "")
    val lines = text.lines()
    val out = ArrayList<Block>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) out += Block.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                flush()
                val fence = trimmed.take(3)
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    code.appendLine(lines[i])
                    i++
                }
                out += Block.Code(code.toString().trimEnd())
            }
            trimmed.isEmpty() -> flush()
            HEADING.matches(trimmed) -> {
                flush()
                val m = HEADING.find(trimmed)!!
                out += Block.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            trimmed.matches(Regex("^([-*_])(\\s*\\1){2,}$")) -> {
                flush()
                out += Block.Rule
            }
            IMAGE_LINE.matches(trimmed) -> {
                flush()
                val m = IMAGE_LINE.find(trimmed)!!
                val url = m.groupValues[2]
                out += if (mediaKind(url) == MediaKind.VIDEO) Block.Video(url, m.groupValues[1]) else Block.Image(url, m.groupValues[1])
            }
            trimmed.startsWith(">") -> {
                flush()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote.appendLine(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                out += Block.Quote(quote.toString().trim())
                continue
            }
            // Attachments Kira copies from Discord: `- [clip.mp4](https://cdn.discordapp.com/...)`, one per line.
            LIST_ITEM.matches(line) && mediaBlock(LIST_ITEM.find(line)!!.groupValues[4]) != null -> {
                flush()
                out += mediaBlock(LIST_ITEM.find(line)!!.groupValues[4])!!
            }
            mediaBlock(trimmed) != null -> {
                flush()
                out += mediaBlock(trimmed)!!
            }
            LIST_ITEM.matches(line) -> {
                flush()
                val m = LIST_ITEM.find(line)!!
                val depth = m.groupValues[1].replace("\t", "  ").length / 2
                val marker = m.groupValues[2]
                val box = m.groupValues[3].trim()
                val checked = if (box.isEmpty()) null else box.contains('x', ignoreCase = true)
                out += Block.Item(if (marker.first().isDigit()) marker else "•", m.groupValues[4], depth, checked)
            }
            else -> {
                // GitHub keeps single line breaks in issues and comments.
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line.trimEnd())
            }
        }
        i++
    }
    flush()
    return out
}

private val INLINE = Regex(
    listOf(
        """`([^`]+)`""",                                  // 1 code
        """\*\*(.+?)\*\*|__(.+?)__""",                   // 2, 3 bold
        """(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""", // 4, 5 italic
        """~~(.+?)~~""",                                  // 6 strike
        """!?\[([^\]]+)]\(([^)\s]+)[^)]*\)""",            // 7, 8 link (images inline become links)
        """(https?://[^\s<>()]+[^\s<>().,;:!?'"])""",     // 9 bare URL
        """(?<![\w@/])@([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))""", // 10 mention
        """(?<![\w&/#])#(\d+)\b""",                       // 11 issue
    ).joinToString("|"),
)

private fun inlineText(text: String, p: Palette, repoUrl: String): AnnotatedString = buildAnnotatedString {
    val link = TextLinkStyles(SpanStyle(color = p.primaryText))
    var at = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(at, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontFamily = GeistMono, fontSize = 13.sp, background = p.grouped)) {
                append(" " + g[1] + " ")
            }
            g[2].isNotEmpty() || g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(inlineText(g[2].ifEmpty { g[3] }, p, repoUrl))
            }
            g[4].isNotEmpty() || g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(inlineText(g[4].ifEmpty { g[5] }, p, repoUrl))
            }
            g[6].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[6]) }
            g[7].isNotEmpty() -> withLink(LinkAnnotation.Url(g[8], link)) { append(g[7]) }
            g[9].isNotEmpty() -> withLink(LinkAnnotation.Url(g[9], link)) { append(prettyUrl(g[9])) }
            g[10].isNotEmpty() -> withLink(LinkAnnotation.Url("https://github.com/" + g[10], link)) {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("@" + g[10]) }
            }
            g[11].isNotEmpty() -> withLink(LinkAnnotation.Url("$repoUrl/issues/" + g[11], link)) { append("#" + g[11]) }
            else -> append(m.value)
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/** `github.com/owner/repo/pull/12` → `#12`, otherwise the URL without its scheme. */
private fun prettyUrl(url: String): String {
    val m = Regex("""github\.com/[^/]+/[^/]+/(?:issues|pull)/(\d+)$""").find(url)
    return if (m != null) "#" + m.groupValues[1] else url.removePrefix("https://").removePrefix("http://")
}

@Composable
fun Markdown(text: String, repoUrl: String, modifier: Modifier = Modifier) {
    val p = Theme.palette
    val blocks = remember(text) { parse(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (blocks.isEmpty()) {
            Text("No description provided.", style = Type.sm.copy(fontStyle = FontStyle.Italic), color = p.tertiary)
        }
        for (block in blocks) {
            when (block) {
                is Block.Heading -> Text(
                    inlineText(block.text, p, repoUrl),
                    style = when (block.level) {
                        1 -> Type.lg
                        2 -> Type.base.copy(fontWeight = FontWeight.SemiBold)
                        else -> Type.sm.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = p.foreground,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is Block.Paragraph -> Text(inlineText(block.text, p, repoUrl), style = Type.sm.copy(lineHeight = 21.sp), color = p.foreground)
                is Block.Item -> Row(Modifier.padding(start = (block.depth * 14).dp)) {
                    val marker = when (block.checked) {
                        true -> "☑"
                        false -> "☐"
                        null -> block.marker
                    }
                    Text(marker, style = Type.sm, color = p.secondary, modifier = Modifier.width(if (block.marker == "•" || block.checked != null) 16.dp else 22.dp))
                    Text(inlineText(block.text, p, repoUrl), style = Type.sm.copy(lineHeight = 21.sp), color = p.foreground)
                }
                is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(p.border))
                    Spacer(Modifier.width(10.dp))
                    Text(inlineText(block.text, p, repoUrl), style = Type.sm.copy(lineHeight = 21.sp), color = p.secondary)
                }
                is Block.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.grouped)
                        .border(1.dp, p.border, RoundedCornerShape(10.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(block.text, style = Type.mono, color = p.foreground, softWrap = false)
                }
                is Block.Image -> MediaImage(block.url, block.alt.ifBlank { "image" })
                is Block.Video -> MediaVideo(block.url, block.name.ifBlank { "video" })
                Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(p.separator))
            }
        }
    }
}
