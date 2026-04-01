package com.example.sekairatingsystem.ui

import com.example.sekairatingsystem.R

data class OshiCharacter(
    val name: String,
    val colorResId: Int,
)

data class OshiUnit(
    val unitName: String,
    val characters: List<OshiCharacter>,
)

val OSHI_UNITS: List<OshiUnit> = listOf(
    OshiUnit(
        unitName = "VIRTUAL SINGER",
        characters = listOf(
            OshiCharacter("ミク", R.color.oshi_miku),
            OshiCharacter("リン", R.color.oshi_rin),
            OshiCharacter("レン", R.color.oshi_len),
            OshiCharacter("ルカ", R.color.oshi_luka),
            OshiCharacter("MEIKO", R.color.oshi_meiko),
            OshiCharacter("KAITO", R.color.oshi_kaito),
        ),
    ),
    OshiUnit(
        unitName = "Leo/need",
        characters = listOf(
            OshiCharacter("一歌", R.color.oshi_ichika),
            OshiCharacter("咲希", R.color.oshi_saki),
            OshiCharacter("穂波", R.color.oshi_honami),
            OshiCharacter("志歩", R.color.oshi_shiho),
        ),
    ),
    OshiUnit(
        unitName = "MORE MORE JUMP!",
        characters = listOf(
            OshiCharacter("みのり", R.color.oshi_minori),
            OshiCharacter("遥", R.color.oshi_haruka),
            OshiCharacter("愛莉", R.color.oshi_airi),
            OshiCharacter("雫", R.color.oshi_shizuku),
        ),
    ),
    OshiUnit(
        unitName = "Vivid BAD SQUAD",
        characters = listOf(
            OshiCharacter("こはね", R.color.oshi_kohane),
            OshiCharacter("杏", R.color.oshi_an),
            OshiCharacter("彰人", R.color.oshi_akito),
            OshiCharacter("冬弥", R.color.oshi_toya),
        ),
    ),
    OshiUnit(
        unitName = "ワンダーランズ×ショウタイム",
        characters = listOf(
            OshiCharacter("司", R.color.oshi_tsukasa),
            OshiCharacter("えむ", R.color.oshi_emu),
            OshiCharacter("寧々", R.color.oshi_nene),
            OshiCharacter("類", R.color.oshi_rui),
        ),
    ),
    OshiUnit(
        unitName = "25時、ナイトコードで。",
        characters = listOf(
            OshiCharacter("奏", R.color.oshi_kanade),
            OshiCharacter("まふゆ", R.color.oshi_mafuyu),
            OshiCharacter("絵名", R.color.oshi_ena),
            OshiCharacter("瑞希", R.color.oshi_mizuki),
        ),
    ),
)

private val OSHI_BY_NAME: Map<String, OshiCharacter> = OSHI_UNITS
    .flatMap { unit -> unit.characters }
    .associateBy { character -> character.name }

fun resolveOshiColorRes(name: String): Int {
    return OSHI_BY_NAME[name]?.colorResId ?: R.color.oshi_miku
}
