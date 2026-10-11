package io.github.hootkit_dev.hootbook.presentation.format

import dev.hootkit.core.*

internal fun coreFailureText(error: Throwable): String = when (error) {
    is CoreException.Invalid -> "请检查 ${error.field}：${error.reason}"
    is CoreException.Conflict -> when (error.reason) {
        ConflictReason.IN_USE -> "仍有交易引用（含回收站）。请先处理交易，或选择归档／连同交易删除。"
        ConflictReason.ACCOUNT_EXISTS -> "同名同尾号账户已存在"
        ConflictReason.CURRENCY_EXISTS -> "账户已有该货币口袋"
        ConflictReason.ANCHOR_EXISTS -> "该时刻已有余额锚点"
        ConflictReason.CATEGORY_EXISTS -> "同类型下已有同名分类"
        ConflictReason.SUBCATEGORY_EXISTS -> "该分类下已有同名子分类"
        else -> "操作冲突：${error.reason}"
    }
    is CoreException.NotFound -> "记录已删除，请刷新"
    else -> "Operation failed; review the input and refresh before retrying"
}
