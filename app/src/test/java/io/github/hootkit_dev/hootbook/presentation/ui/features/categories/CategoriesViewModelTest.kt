package io.github.hootkit_dev.hootbook.presentation.ui.features.categories

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest : CoreViewModelTest() {
    private val category = Category("uuid", CategoryKind.EXPENSE, null, "Food", "Food", "type_food_dining", "#123456", 0, false, emptyList())
    private inner class Fake : HootKitCore(NoPointer) {
        var rows = listOf(category)
        var onCreate: suspend () -> Unit = {}
        var creates = 0
        var onCount: suspend (String) -> UInt = { 1u }
        var deletion: Pair<String, CategoryRef?>? = null
        override fun categories() = object : Categories(NoPointer) {
            override suspend fun list(kind: CategoryKind?, includeHidden: Boolean) = rows
            override suspend fun transactionCount(categoryId: String) = onCount(categoryId)
            override suspend fun create(input: NewCategory): String { creates++; onCreate(); return "new" }
            override suspend fun delete(categoryId: String, moveTo: CategoryRef?) { deletion = categoryId to moveTo }
        }
    }
    @Test fun loadsRefreshesAndKeepsLatestSearch() = runTest {
        val core = Fake(); val changes = ChangeBus()
        val vm = keep(CategoriesViewModel(CoreSession(backgroundScope) { core }, changes))
        awaitState { !vm.uiState.value.isLoading }
        assertSame(category, vm.uiState.value.categories[category.id])
        vm.updateSearchQuery("old"); vm.updateSearchQuery("new")
        core.rows = listOf(category.copy(displayName = "Changed")); changes.changed(Table.CATEGORIES)
        awaitState { vm.uiState.value.categories[category.id]?.displayName == "Changed" }
        assertEquals("new", vm.uiState.value.searchQuery)
    }
    @Test fun saveGuardsDoubleSubmitAndRetainsEditorOnError() = runTest {
        val core = Fake(); val gate = CompletableDeferred<Unit>()
        core.onCreate = { gate.await(); error("save failed") }
        val vm = keep(CategoriesViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        vm.showAddDialog(); vm.saveCategory("Food", "#123456", "food", false); vm.saveCategory("Food", "#123456", "food", false)
        awaitState { core.creates == 1 }; assertTrue(vm.uiState.value.saving)
        gate.complete(Unit); awaitState { !vm.uiState.value.saving }
        assertTrue(vm.uiState.value.showAddEditDialog); assertEquals("save failed", vm.uiState.value.errorMessage)
        core.onCreate = {}; vm.saveCategory("Food", "#123456", "food", false)
        awaitState { !vm.uiState.value.saving }; assertFalse(vm.uiState.value.showAddEditDialog)
    }
    @Test fun newestDeletionCheckWinsAndMigrationIsSingleCoreCommand() = runTest {
        val core = Fake(); val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        core.onCount = { id -> if (id == "old") withContext(NonCancellable) { started.complete(Unit); gate.await() }; 1u }
        val vm = keep(CategoriesViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        vm.deleteCategory(category.copy(id = "old")); awaitState { started.isCompleted }
        vm.deleteCategory(category); awaitState { vm.uiState.value.categoryToDelete?.id == category.id }
        gate.complete(Unit); runCurrent()
        vm.confirmMigrationToCategory(category.copy(id = "target"), null)
        awaitState { !vm.uiState.value.saving }
        assertEquals(category.id to CategoryRef("target", null), core.deletion)
    }
}
