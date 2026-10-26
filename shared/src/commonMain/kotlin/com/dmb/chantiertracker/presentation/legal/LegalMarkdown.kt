package com.dmb.chantiertracker.presentation.legal

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

sealed interface LegalLink {
    val target: String

    data class Web(override val target: String) : LegalLink
    data class Mail(override val target: String) : LegalLink
}

data class LegalSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: LegalLink? = null,
)

data class LegalInline(val spans: List<LegalSpan>) {
    val plainText: String get() = spans.joinToString("") { it.text }

    constructor(vararg spans: LegalSpan) : this(spans.toList())
}

sealed interface LegalBlock {
    data class Heading(val level: Int, val text: LegalInline) : LegalBlock
    data class Paragraph(val text: LegalInline) : LegalBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<List<LegalBlock>>) : LegalBlock
    data class Quote(val blocks: List<LegalBlock>) : LegalBlock
    data class Code(val text: String) : LegalBlock
    data object Rule : LegalBlock
}

private val PENDING_TEXT = Regex("""\[(?:À COMPLÉTER|TO BE COMPLETED)[^\]\n]*]?""")

fun pendingRanges(text: String): List<IntRange> = PENDING_TEXT.findAll(text).map { it.range }.toList()

// CommonMark core, like the backend's commonmark-java validator: no HTML is interpreted, no image is
// loaded, and only http, https and mailto targets become links.
fun parseLegalMarkdown(markdown: String): List<LegalBlock> {
    val root = MarkdownParser(CommonMarkFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    return LegalMarkdownReader(markdown, LinkMap.buildLinkMap(root, markdown)).blocks(root.children)
}

private val HEADING_LEVELS: Map<IElementType, Int> = mapOf(
    MarkdownElementTypes.ATX_1 to 1, MarkdownElementTypes.ATX_2 to 2, MarkdownElementTypes.ATX_3 to 3,
    MarkdownElementTypes.ATX_4 to 4, MarkdownElementTypes.ATX_5 to 5, MarkdownElementTypes.ATX_6 to 6,
    MarkdownElementTypes.SETEXT_1 to 1, MarkdownElementTypes.SETEXT_2 to 2,
)

private val HEADING_MARKS = setOf(MarkdownTokenTypes.ATX_HEADER, MarkdownTokenTypes.SETEXT_1, MarkdownTokenTypes.SETEXT_2)

private val SKIPPED_BLOCKS = setOf(
    MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER,
    MarkdownTokenTypes.BLOCK_QUOTE, MarkdownElementTypes.LINK_DEFINITION,
)

private data class Style(val bold: Boolean = false, val italic: Boolean = false, val link: LegalLink? = null)

private class Run(val raw: StringBuilder, val style: Style, val literal: Boolean, val code: Boolean)

private class LegalMarkdownReader(private val source: String, private val links: LinkMap) {

    private fun ASTNode.raw(): String = source.substring(startOffset, endOffset)

    fun blocks(nodes: List<ASTNode>): List<LegalBlock> = nodes.mapNotNull { block(it) }

    private fun block(node: ASTNode): LegalBlock? {
        val type = node.type
        HEADING_LEVELS[type]?.let { level ->
            return Heading(level, inline(node.children.filter { it.type !in HEADING_MARKS }))
        }
        return when (type) {
            in SKIPPED_BLOCKS -> null
            MarkdownElementTypes.PARAGRAPH -> inline(node.children).takeIf { it.spans.isNotEmpty() }?.let { LegalBlock.Paragraph(it) }
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> list(node)
            MarkdownElementTypes.BLOCK_QUOTE -> LegalBlock.Quote(blocks(node.children))
            MarkdownElementTypes.CODE_FENCE -> LegalBlock.Code(
                node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }.joinToString("\n") { it.raw() },
            )
            MarkdownElementTypes.CODE_BLOCK -> LegalBlock.Code(
                node.children.filter { it.type == MarkdownTokenTypes.CODE_LINE }.joinToString("\n") { it.raw().removeIndent() },
            )
            MarkdownElementTypes.HTML_BLOCK -> node.raw().trim().takeIf { it.isNotEmpty() }?.let { LegalBlock.Paragraph(LegalInline(LegalSpan(it))) }
            MarkdownTokenTypes.HORIZONTAL_RULE -> LegalBlock.Rule
            else -> inline(node.children.ifEmpty { listOf(node) }).takeIf { it.spans.isNotEmpty() }?.let { LegalBlock.Paragraph(it) }
        }
    }

    private fun list(node: ASTNode): LegalBlock.ListBlock {
        val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
        val start = items.firstOrNull()?.children?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
            ?.raw()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 1
        return LegalBlock.ListBlock(
            ordered = node.type == MarkdownElementTypes.ORDERED_LIST,
            start = start,
            items = items.map { blocks(it.children) },
        )
    }

    private fun inline(nodes: List<ASTNode>): LegalInline {
        val runs = mutableListOf<Run>()
        collectAll(nodes, Style(), runs)
        return LegalInline(finish(runs))
    }

    private fun MutableList<Run>.append(text: String, style: Style, literal: Boolean = false, code: Boolean = false) {
        val last = lastOrNull()
        if (last != null && last.style == style && last.literal == literal && last.code == code) last.raw.append(text)
        else add(Run(StringBuilder(text), style, literal, code))
    }

    private fun collectAll(nodes: List<ASTNode>, style: Style, runs: MutableList<Run>) {
        nodes.forEachIndexed { index, node ->
            val aroundEmail = (node.type == MarkdownTokenTypes.LT && nodes.getOrNull(index + 1)?.type == MarkdownTokenTypes.EMAIL_AUTOLINK) ||
                (node.type == MarkdownTokenTypes.GT && nodes.getOrNull(index - 1)?.type == MarkdownTokenTypes.EMAIL_AUTOLINK)
            if (!aroundEmail) collect(node, style, runs)
        }
    }

    private fun collect(node: ASTNode, style: Style, runs: MutableList<Run>) {
        when (node.type) {
            MarkdownElementTypes.EMPH -> collectAll(node.children.filter { it.type != MarkdownTokenTypes.EMPH }, style.copy(italic = true), runs)
            MarkdownElementTypes.STRONG -> collectAll(node.children.filter { it.type != MarkdownTokenTypes.EMPH }, style.copy(bold = true), runs)
            MarkdownElementTypes.CODE_SPAN -> runs.append(codeSpan(node), style, literal = true, code = true)
            MarkdownElementTypes.INLINE_LINK -> {
                val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                    ?.let { LinkMap.normalizeDestination(it.raw(), true).toString() }
                linkText(node, style.copy(link = destination?.let(::legalLinkOrNull)), runs)
            }
            MarkdownElementTypes.FULL_REFERENCE_LINK, MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
                val destination = label?.let { links.getLinkInfo(it.raw())?.destination?.toString() }
                val linked = style.copy(link = destination?.let(::legalLinkOrNull))
                if (node.type == MarkdownElementTypes.SHORT_REFERENCE_LINK) label?.let { bracketed(it, linked, runs) }
                else linkText(node, linked, runs)
            }
            MarkdownElementTypes.AUTOLINK -> autolink(node, style, runs)
            MarkdownTokenTypes.EMAIL_AUTOLINK -> runs.append(node.raw(), style.copy(link = LegalLink.Mail("mailto:${node.raw()}")), literal = true)
            MarkdownElementTypes.IMAGE -> node.children.firstOrNull { it.type != MarkdownTokenTypes.EXCLAMATION_MARK }
                ?.let { linkText(it, style.copy(link = null), runs, keepLinks = false) }
            MarkdownTokenTypes.HTML_TAG -> runs.append(node.raw(), style, literal = true)
            MarkdownTokenTypes.EOL -> runs.append(" ", style)
            MarkdownTokenTypes.HARD_LINE_BREAK -> runs.append("\n", style)
            else -> if (node.children.isEmpty()) runs.append(node.raw(), style) else collectAll(node.children, style, runs)
        }
    }

    private fun linkText(node: ASTNode, style: Style, runs: MutableList<Run>, keepLinks: Boolean = true) {
        val text = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
            ?: node.takeIf { it.type == MarkdownElementTypes.LINK_LABEL }
            ?: node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
            ?: return
        bracketed(text, if (keepLinks) style else style.copy(link = null), runs)
    }

    private fun bracketed(node: ASTNode, style: Style, runs: MutableList<Run>) {
        val inner = node.children
        val content = if (inner.size >= 2 && inner.first().type == MarkdownTokenTypes.LBRACKET && inner.last().type == MarkdownTokenTypes.RBRACKET) {
            inner.subList(1, inner.size - 1)
        } else {
            inner
        }
        collectAll(content, style, runs)
    }

    private fun autolink(node: ASTNode, style: Style, runs: MutableList<Run>) {
        val token = node.children.firstOrNull { it.type == MarkdownTokenTypes.AUTOLINK || it.type == MarkdownTokenTypes.EMAIL_AUTOLINK }
        if (token == null) {
            runs.append(node.raw(), style, literal = true)
            return
        }
        val address = token.raw()
        val link = if (token.type == MarkdownTokenTypes.EMAIL_AUTOLINK) LegalLink.Mail("mailto:$address") else legalLinkOrNull(address)
        runs.append(address, style.copy(link = link), literal = true)
    }

    private fun codeSpan(node: ASTNode): String {
        val children = node.children
        val first = children.firstOrNull { it.type == MarkdownTokenTypes.BACKTICK } ?: return node.raw()
        val last = children.lastOrNull { it.type == MarkdownTokenTypes.BACKTICK }?.takeIf { it !== first } ?: return node.raw()
        val content = source.substring(first.endOffset, last.startOffset).replace('\n', ' ')
        return if (content.length > 2 && content.startsWith(' ') && content.endsWith(' ') && content.isNotBlank()) content.substring(1, content.length - 1) else content
    }

    private fun finish(runs: List<Run>): List<LegalSpan> {
        val spans = runs.map { run ->
            val text = if (run.literal) run.raw.toString() else EntityConverter.replaceEntities(run.raw, true, true)
            LegalSpan(
                text = if (run.code) text else text.replace(SPACES, " ").replace(SPACES_AROUND_BREAK, "\n"),
                bold = run.style.bold,
                italic = run.style.italic,
                code = run.code,
                link = run.style.link,
            )
        }
        return spans.trimmed().filter { it.text.isNotEmpty() }
    }

    private fun List<LegalSpan>.trimmed(): List<LegalSpan> {
        if (isEmpty()) return this
        val result = toMutableList()
        result[0] = result[0].copy(text = result[0].text.trimStart())
        result[result.lastIndex] = result[result.lastIndex].copy(text = result[result.lastIndex].text.trimEnd())
        return result
    }

    private fun String.removeIndent(): String {
        var removed = 0
        while (removed < 4 && removed < length && this[removed] == ' ') removed++
        return substring(removed)
    }
}

private val SPACES = Regex("[ \\t]+")
private val SPACES_AROUND_BREAK = Regex(" ?\n ?")

private typealias Heading = LegalBlock.Heading

private fun legalLinkOrNull(destination: String): LegalLink? {
    val target = destination.trim()
    val lower = target.lowercase()
    return when {
        lower.startsWith("https://") || lower.startsWith("http://") -> LegalLink.Web(target)
        lower.startsWith("mailto:") && target.length > "mailto:".length -> LegalLink.Mail(target)
        else -> null
    }
}
