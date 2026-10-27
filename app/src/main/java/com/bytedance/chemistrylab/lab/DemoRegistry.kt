package com.bytedance.chemistrylab.lab

import com.bytedance.chemistrylab.demos.crystal.CrystalDemo
import com.bytedance.chemistrylab.demos.daniell.DaniellDemo
import com.bytedance.chemistrylab.demos.electrolysis.ElectrolysisDemo
import com.bytedance.chemistrylab.demos.elephant.ElephantDemo
import com.bytedance.chemistrylab.demos.equilibrium.EquilibriumDemo
import com.bytedance.chemistrylab.demos.flame.FlameDemo
import com.bytedance.chemistrylab.demos.rates.RatesDemo
import com.bytedance.chemistrylab.demos.titration.TitrationDemo
import com.bytedance.chemistrylab.demos.vsepr.VseprDemo

/** Subjects = the catalogue's tabs: structure of matter -> electrochemistry -> how reactions go. */
enum class Subject(private val zh: String, private val en: String) {
    STRUCTURE("物质结构", "Structure"), ELECTRO("电化学", "Electrochemistry"), REACTION("反应原理", "Reactions");  // i18n-ok (zh/en pairs)
    val label: String get() = tr(zh, en)
}

/**
 * The catalogue. Order inside a subject = order on the cards (atoms -> molecules -> reactions). Adding a
 * demo = one line here, plus a thumbnail (render it with LabReviewTest, then `python3 tools/make_thumbs.py`).
 * Every chemistry demo shows the macroscopic phenomenon AND a microscopic molecular view side by side.
 */
object DemoRegistry {
    val catalog: List<Pair<Demo, Subject>> = listOf(
        FlameDemo to Subject.STRUCTURE,       // 焰色反应与原子光谱       Bunsen & Kirchhoff 1860
        VseprDemo to Subject.STRUCTURE,       // 分子构型 · 价层电子对互斥  Gillespie–Nyholm 1957
        CrystalDemo to Subject.STRUCTURE,     // 晶体结构                 Bragg 1913
        ElectrolysisDemo to Subject.ELECTRO,  // 电解水                   Nicholson & Carlisle 1800
        DaniellDemo to Subject.ELECTRO,       // 原电池 · 铜锌电池         Daniell 1836
        TitrationDemo to Subject.REACTION,    // 酸碱中和滴定             Gay-Lussac 1824
        EquilibriumDemo to Subject.REACTION,  // 化学平衡 · 勒夏特列原理  Le Chatelier 1884
        RatesDemo to Subject.REACTION,        // 反应速率 · 碰撞理论       Arrhenius 1889
        ElephantDemo to Subject.REACTION,     // 大象牙膏 · 催化分解       H₂O₂ + KI
    )
    val all: List<Demo> = catalog.map { it.first }

    /** The demo the app opens on: the most spectacular first impression. */
    const val FIRST = "elephant"

    /** English titles for the catalogue / top bar (each demo's own `title` is the Chinese one). */
    private val TITLE_EN = mapOf(
        "flame" to "Flame Tests & Atomic Spectra",
        "vsepr" to "Molecular Shapes · VSEPR",
        "crystal" to "Crystal Structures",
        "electrolysis" to "Electrolysis of Water",
        "daniell" to "Galvanic Cell · Daniell",
        "titration" to "Acid–Base Titration",
        "equilibrium" to "Chemical Equilibrium · Le Chatelier",
        "rates" to "Reaction Rates · Collision Theory",
        "elephant" to "Elephant Toothpaste · Catalysis",
    )
    fun titleOf(d: Demo): String = tr(d.title, TITLE_EN[d.id] ?: d.title)
    /** Catalogue-card title: one line in a 225 dp card, so English drops its " · subtitle" (Chinese too when > 12 chars). */
    fun cardTitleOf(d: Demo): String = tr(if (d.title.length > 12) d.title.substringBefore(" · ") else d.title,
        (CARD_EN[d.id] ?: TITLE_EN[d.id] ?: d.title).substringBefore(" · "))
    private val CARD_EN = mapOf("flame" to "Flame Tests", "vsepr" to "Molecular Shapes", "crystal" to "Crystal Structures", "electrolysis" to "Electrolysis", "daniell" to "Galvanic Cell", "titration" to "Titration", "rates" to "Reaction Rates",
        "equilibrium" to "Chemical Equilibrium", "elephant" to "Elephant Toothpaste")
    fun subjectOf(id: String) = catalog.firstOrNull { it.first.id == id }?.second
}
