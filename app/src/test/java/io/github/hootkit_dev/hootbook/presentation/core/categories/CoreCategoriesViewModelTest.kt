package io.github.hootkit_dev.hootbook.presentation.core.categories

import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreCategoriesViewModelTest {
    private fun api(handler: (String, Array<out Any?>) -> Any?): CategoriesInterface = Proxy.newProxyInstance(
        CategoriesInterface::class.java.classLoader, arrayOf(CategoriesInterface::class.java),
    ) { _, method, args -> handler(method.name.substringBefore('-'), args ?: emptyArray()) } as CategoriesInterface
    private fun category(id: String, kind: CategoryKind = CategoryKind.EXPENSE) = Category(id, kind, null, id, id, "icon", "#001122", 0, false, emptyList())
    @Test fun changingFilterCancelsOldQueryAndPreservesGeneratedRecord() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            var old: Continuation<List<Category>>? = null
            val new = category("income-uuid", CategoryKind.INCOME)
            val model = CoreCategoriesViewModel(ChangeBus()) { api { method, args ->
                check(method == "list")
                if (args[0] == CategoryKind.EXPENSE) {
                    @Suppress("UNCHECKED_CAST")
                    old = args.last() as Continuation<List<Category>>
                    COROUTINE_SUSPENDED
                } else listOf(new)
            } }
            store.put("test", model); runCurrent()
            model.filter(CategoryKind.INCOME); runCurrent()
            assertSame(new, model.state.value.categories.single())
            old!!.resume(listOf(category("stale-expense"))); runCurrent()
            assertSame(new, model.state.value.categories.single())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun failureDoesNotCloseEditorOrLoseSubcategoryParent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val model = CoreCategoriesViewModel(ChangeBus()) { api { method, _ -> when (method) {
                "list" -> emptyList<Category>()
                "createSubcategory" -> throw CoreException.Conflict(ConflictReason.SUBCATEGORY_EXISTS)
                else -> error(method)
            } } }
            store.put("test", model); runCurrent()
            model.createSubcategory(category("parent-uuid"))
            model.draftChanged(model.state.value.draft!!.copy(name = "重复名称")); model.save(); runCurrent()
            assertEquals("parent-uuid", model.state.value.draft!!.parentId)
            assertEquals("重复名称", model.state.value.draft!!.name)
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.saving)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun backClosesDraftThenCategoryBeforeHostPop() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val row = category("parent-uuid")
            val model = CoreCategoriesViewModel(ChangeBus()) { api { method, _ -> when (method) {
                "list" -> listOf(row)
                "transactionCount" -> 0u
                else -> error(method)
            } } }
            store.put("test", model); runCurrent()
            model.select(row.id); runCurrent()
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            assertSame(row, model.state.value.selected)
            model.createSubcategory(row)
            assertTrue(model.back()); assertNull(model.state.value.draft)
            assertEquals(row.id, model.state.value.selectedId)
            assertTrue(model.back()); runCurrent(); assertNull(model.state.value.selectedId)
            assertFalse(model.back())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun savingBackFilterAndSelectionKeepSubcategoryCommandParent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val row = category("parent-uuid")
            var target: String? = null
            var write: Continuation<String>? = null
            val model = CoreCategoriesViewModel(ChangeBus()) { api { method, args -> when (method) {
                "list" -> listOf(row)
                "transactionCount" -> 0u
                "createSubcategory" -> {
                    target = (args[0] as NewSubcategory).categoryId
                    @Suppress("UNCHECKED_CAST")
                    write = args.last() as Continuation<String>
                    COROUTINE_SUSPENDED
                }
                else -> error(method)
            } } }
            store.put("test", model); runCurrent()
            model.select(row.id); runCurrent()
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            assertSame(row, model.state.value.selected)
            model.createSubcategory(row)
            model.draftChanged(model.state.value.draft!!.copy(name = "子分类")); model.save(); runCurrent()
            assertTrue(model.back())
            model.filter(CategoryKind.INCOME); model.select("other-uuid")
            assertEquals(CategoryKind.EXPENSE, model.state.value.kind)
            assertEquals(row.id, model.state.value.selectedId)
            assertEquals(row.id, model.state.value.draft!!.parentId)
            assertTrue(model.state.value.navigationBlocked)
            assertEquals(row.id, target)
            write!!.resume("new-subcategory-uuid"); runCurrent()
            assertFalse(model.state.value.saving)
            assertFalse(model.state.value.navigationBlocked)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
