package io.github.hootkit_dev.hootbook.presentation.core.categories

import dev.hootkit.core.*
import org.junit.Assert.*
import org.junit.Test

class CoreCategoryFormsTest {
    @Test fun draftUsesStringIdsAndExplicitParent() {
        val input = CoreCategoryDraft(parentId = "category-uuid", name = "  早餐  ").newSubcategory()
        assertEquals("category-uuid", input.categoryId)
        assertEquals("早餐", input.name)
        assertThrows(IllegalArgumentException::class.java) { CoreCategoryDraft().newSubcategory() }
    }
    @Test fun categoryKindIsNotPartOfPatch() {
        val input = CoreCategoryDraft(kind = CategoryKind.INCOME, name = "  奖金 ", color = " #001122 ").newCategory()
        assertEquals(CategoryKind.INCOME, input.kind)
        assertEquals("奖金", input.name)
        assertEquals("#001122", input.color)
    }
    @Test fun reorderRetainsWholeListIncludingHiddenIds() {
        val ids = mutableListOf("visible", "hidden", "builtin")
        assertTrue(moveId(ids, "hidden", -1))
        assertEquals(listOf("hidden", "visible", "builtin"), ids)
        assertFalse(moveId(ids, "missing", 1))
        assertFalse(moveId(ids, "hidden", -1))
        assertEquals(3, ids.toSet().size)
    }
    @Test fun fallbackBuiltinsStayVisibleWhileOthersCanHide() {
        fun category(key: String?) = Category("id", CategoryKind.EXPENSE, key, null, "其他", "icon", "#001122", 0, false, emptyList())
        assertFalse(canHide(category("other_expense")))
        assertFalse(canHide(category("other_income")))
        assertTrue(canHide(category("food")))
        assertTrue(canHide(category(null)))
    }
}
