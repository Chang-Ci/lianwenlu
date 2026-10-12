package com.lianwenqi.app.util

/** 本地人名 / 地名生成器（离线可用，AI 生成为可选增强） */
object NameGenerator {

    private val surnames = listOf(
        "赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈", "褚", "卫", "蒋", "沈",
        "韩", "杨", "朱", "秦", "尤", "许", "何", "吕", "施", "张", "孔", "曹", "严", "华",
        "金", "魏", "陶", "姜", "戚", "谢", "邹", "喻", "柏", "水", "窦", "章", "云", "苏",
        "潘", "葛", "范", "彭", "郎", "鲁", "韦", "昌", "马", "苗", "凤", "花", "方", "任",
        "袁", "柳", "唐", "罗", "薛", "雷", "贺", "倪", "汤", "滕", "殷", "顾", "陆", "裴"
    )

    private val maleChars = listOf(
        "辰", "轩", "毅", "霆", "皓", "晨", "泽", "宇", "衡", "霄", "衍", "烬", "朔", "屿",
        "骞", "璟", "昱", "渊", "澈", "野", "砚", "凌", "尘", "啸", "峥", "谦", "牧", "钧"
    )

    private val femaleChars = listOf(
        "婉", "清", "语", "宁", "颜", "昭", "琳", "汐", "栀", "绾", "茵", "晚", "若", "微",
        "雪", "瑶", "筝", "笙", "菱", "菁", "茗", "妤", "皎", "棠", "歌", "霓", "绾", "萦"
    )

    private val neutralChars = listOf(
        "书", "言", "南", "之", "白", "青", "远", "临", "星", "阙", "澜", "舟", "禾", "安",
        "辞", "阮", "岑", "洛", "祁", "昀", "栎", "晞", "奕", "叙", "宴", "重"
    )

    private val placeHeads = listOf(
        "青", "玄", "碧", "苍", "落", "栖", "临", "望", "长", "云", "幽", "寒", "赤", "白",
        "北", "南", "锦", "灵", "孤", "浮", "重", "千"
    )

    private val placeTails = listOf(
        "城", "州", "川", "谷", "海", "渊", "林", "山", "镇", "渡", "关", "崖", "原", "丘",
        "洲", "岛", "门", "亭", "台", "阁", "巷", "滩", "窟", "港"
    )

    private val placeSub = listOf("", "之", "内", "畔", "境", "外")

    /** gender: 0 随机，1 男，2 女 */
    fun personName(gender: Int = 0): String {
        val surname = surnames.random()
        val pool = when (gender) {
            1 -> maleChars
            2 -> femaleChars
            else -> neutralChars + if (kotlin.random.Random.nextBoolean()) maleChars else femaleChars
        }
        val given = if (kotlin.random.Random.nextInt(10) == 0) {
            pool.random().toString()
        } else {
            pool.random().toString() + pool.random()
        }
        return surname + given
    }

    fun placeName(): String =
        placeHeads.random().toString() + placeTails.random() + placeSub.random()

    fun personNames(count: Int, gender: Int = 0): List<String> =
        generateSequence { personName(gender) }.distinct().take(count).toList()

    fun placeNames(count: Int): List<String> =
        generateSequence { placeName() }.distinct().take(count).toList()
}