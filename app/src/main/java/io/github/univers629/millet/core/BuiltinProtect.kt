package io.github.univers629.millet.core

/**
 * 内置免冻名单：
 * - soterserver / musicfx：冻结后会造成指纹支付、系统音效延迟，且日志显示被反复冻结/解冻。
 * - Shizuku 管理器与 XMillet 自身：Shizuku 降级模式下特权通道依赖这两个进程的 binder，
 *   一旦被冻结，PrivilegedExecutor 的调用会卡死在 binder transaction 上，表现为降级模式整体失效。
 */
object BuiltinProtect {
    val FREEZE_EXEMPT_PACKAGES: Set<String> = setOf(
        "com.tencent.soter.soterserver",
        "com.android.musicfx",
        "moe.shizuku.privileged.api",
        "io.github.univers629.millet",
    )
}
