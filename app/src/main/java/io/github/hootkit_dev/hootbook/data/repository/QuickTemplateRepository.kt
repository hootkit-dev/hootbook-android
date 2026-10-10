package io.github.hootkit_dev.hootbook.data.repository

import io.github.hootkit_dev.hootbook.data.database.dao.QuickTemplateDao
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickTemplateRepository @Inject constructor(
    private val dao: QuickTemplateDao
) {
    val templates: Flow<List<QuickTemplateEntity>> = dao.getAll()

    suspend fun getById(id: Long): QuickTemplateEntity? = dao.getById(id)

    /** New templates go to the end of the list. */
    suspend fun add(template: QuickTemplateEntity): Long {
        val order = dao.count()
        return dao.insert(template.copy(id = 0, sortOrder = order))
    }

    suspend fun update(template: QuickTemplateEntity) {
        dao.update(template.copy(updatedAt = LocalDateTime.now()))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Persist a new order for the given templates; index in the list becomes [QuickTemplateEntity.sortOrder]. */
    suspend fun reorder(ordered: List<QuickTemplateEntity>) {
        ordered.forEachIndexed { index, t ->
            if (t.sortOrder != index) dao.update(t.copy(sortOrder = index, updatedAt = LocalDateTime.now()))
        }
    }
}
