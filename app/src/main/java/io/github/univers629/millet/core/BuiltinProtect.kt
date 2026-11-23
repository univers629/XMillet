package io.github.univers629.millet.core

/** 内置免冻名单：冻结后会造成指纹支付、系统音效延迟，且日志显示被反复冻结/解冻。 */
object BuiltinProtect {
    val FREEZE_EXEMPT_PACKAGES: Set<String> = setOf(
        "com.tencent.soter.soterserver",
        "com.android.musicfx",
    )
}
