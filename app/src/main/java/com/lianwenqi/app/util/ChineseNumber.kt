package com.lianwenqi.app.util

/** 阿拉伯数字 -> 中文数字，用于 %N 前缀 */
object ChineseNumber {
    private val digits = charArrayOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')

    fun format(n: Int): String {
        if (n <= 0) return "零"
        if (n < 10) return digits[n].toString()
        if (n < 20) return "十" + if (n == 10) "" else digits[n % 10].toString()
        if (n < 100) {
            val tens = n / 10
            val ones = n % 10
            return digits[tens].toString() + "十" + if (ones == 0) "" else digits[ones].toString()
        }
        if (n < 1000) {
            val hundreds = n / 100
            val rest = n % 100
            return digits[hundreds].toString() + "百" + if (rest == 0) "" else
                (if (rest < 10) "零" else "") + format(rest)
        }
        return n.toString()
    }

    /** 中文数字 -> 阿拉伯数字，解析失败返回 null；支持到 999 */
    fun parse(text: String): Int? {
        if (text.isEmpty()) return null
        var result = 0
        var current = 0
        for (ch in text) {
            val d = digits.indexOf(ch)
            when {
                d >= 0 -> current = d
                ch == '十' -> {
                    result += (if (current == 0) 1 else current) * 10
                    current = 0
                }
                ch == '百' -> {
                    result += (if (current == 0) 1 else current) * 100
                    current = 0
                }
                else -> return null
            }
        }
        return result + current
    }
}

/** 依据前缀模板生成标题：%n 阿拉伯数字，%N 中文数字 */
fun buildTitle(prefix: String, index: Int): String =
    prefix.replace("%n", index.toString()).replace("%N", ChineseNumber.format(index))

/**
 * 依据前缀模板，从已有标题中解析出最大编号，返回下一个可用编号。
 * 例：已有「第1章」「第3章」，前缀「第%n章」-> 4；没有任何匹配则返回 1。
 */
fun nextTitleIndex(prefix: String, titles: List<String>): Int {
    val regex = StringBuilder()
    val kinds = mutableListOf<Char>()
    var i = 0
    while (i < prefix.length) {
        val c = prefix[i]
        if (c == '%' && i + 1 < prefix.length && (prefix[i + 1] == 'n' || prefix[i + 1] == 'N')) {
            if (prefix[i + 1] == 'n') {
                regex.append("(\\d+)")
                kinds.add('n')
            } else {
                regex.append("([零一二三四五六七八九十百]+)")
                kinds.add('N')
            }
            i += 2
        } else {
            regex.append(Regex.escape(c.toString()))
            i++
        }
    }
    if (kinds.isEmpty()) return titles.size + 1
    val matcher = Regex(regex.toString())
    var max = 0
    titles.forEach { title ->
        // 用 find 而非全文匹配，兼容「第2章 试炼」这类带后缀的标题
        val m = matcher.find(title) ?: return@forEach
        for ((gi, kind) in kinds.withIndex()) {
            val raw = m.groupValues[gi + 1]
            val num = if (kind == 'n') raw.toIntOrNull() else ChineseNumber.parse(raw)
            if (num != null) {
                if (num > max) max = num
                break
            }
        }
    }
    return max + 1
}