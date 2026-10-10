
package io.github.hootkit_dev.hootbook.data.repository

import android.content.Context
import io.github.hootkit_dev.hootbook.data.database.dao.SubcategoryDao
import io.github.hootkit_dev.hootbook.data.database.dao.CategoryDao
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubcategoryRepository @Inject constructor(
    private val subcategoryDao: SubcategoryDao,
    private val categoryDao: CategoryDao,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val externalScope: CoroutineScope
) {
    val subcategoriesMap: StateFlow<Map<Long, List<SubcategoryEntity>>> = subcategoryDao.getAllSubcategories()
        .map { allSubs -> allSubs.groupBy { it.categoryId } }
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )
    fun getSubcategoriesByCategoryId(categoryId: Long): Flow<List<SubcategoryEntity>> {
        return subcategoryDao.getSubcategoriesByCategoryId(categoryId)
    }

    fun getAllSubcategories(): Flow<List<SubcategoryEntity>> {
        return subcategoryDao.getAllSubcategories()
    }

    suspend fun getSubcategoryByName(name: String): SubcategoryEntity? {
        return subcategoryDao.getSubcategoryByName(name)
    }

    suspend fun createSubcategory(
        categoryId: Long,
        name: String,
        iconResId: Int = 0,
        iconName: String = "",
        color: String = "#757575"
    ): Long {
        val subcategory = SubcategoryEntity(
            categoryId = categoryId,
            name = name,
            iconResId = iconResId,
            iconName = iconName,
            color = color,
            isSystem = false
        )
        return subcategoryDao.insertSubcategory(subcategory)
    }

    suspend fun updateSubcategory(subcategory: SubcategoryEntity) {
        subcategoryDao.updateSubcategory(
            subcategory.copy(updatedAt = LocalDateTime.now())
        )
    }

    suspend fun getSubcategoryById(subcategoryId: Long): SubcategoryEntity? = subcategoryDao.getSubcategoryById(subcategoryId)

    suspend fun resetSubcategoryToDefault(subcategoryId: Long) {
        val subcategory = subcategoryDao.getSubcategoryById(subcategoryId)
        if (subcategory != null && subcategory.isSystem) {
            // Reset to default values
            val resetSubcategory = subcategory.copy(
                name = subcategory.defaultName ?: subcategory.name,
                iconResId = subcategory.defaultIconResId ?: subcategory.iconResId,
                iconName = subcategory.defaultIconName ?: subcategory.iconName,
                color = subcategory.defaultColor ?: subcategory.color,
                updatedAt = LocalDateTime.now()
            )
            subcategoryDao.updateSubcategory(resetSubcategory)
        }
    }

    suspend fun deleteSubcategory(subcategory: SubcategoryEntity): Boolean {
        // Only delete non-system subcategories
        if (!subcategory.isSystem) {
            subcategoryDao.deleteSubcategory(subcategory)
            return true
        }
        return false
    }

    suspend fun deleteSubcategoryById(subcategoryId: Long): Boolean {
        val subcategory = subcategoryDao.getSubcategoryById(subcategoryId)
        if (subcategory != null && !subcategory.isSystem) {
            subcategoryDao.deleteSubcategoryById(subcategoryId)
            return true
        }
        return false
    }

}
