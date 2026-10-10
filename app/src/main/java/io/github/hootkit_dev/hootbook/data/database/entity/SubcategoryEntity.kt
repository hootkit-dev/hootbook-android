package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
        tableName = "subcategories",
        indices = [Index(value = ["sync_id"]), Index(value = ["category_id"])],
        foreignKeys =
                [
                        ForeignKey(
                                entity = CategoryEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["category_id"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
data class SubcategoryEntity(
        @PrimaryKey(autoGenerate = true) 
        @ColumnInfo(name = "id") 
        val id: Long = 0,
        
        @ColumnInfo(name = "category_id") 
        val categoryId: Long,
        
        @ColumnInfo(name = "name") 
        val name: String,
        
        @ColumnInfo(name = "icon_res_id", defaultValue = "0") 
        val iconResId: Int = 0,
        
        @ColumnInfo(name = "icon_name", defaultValue = "") 
        val iconName: String = "",
        
        @ColumnInfo(name = "color", defaultValue = "#757575") 
        val color: String = "#757575",
        
        @ColumnInfo(name = "is_system", defaultValue = "0") 
        val isSystem: Boolean = false,
        
        // Default values for reset functionality (null for user-created subcategories)
        @ColumnInfo(name = "default_name") 
        val defaultName: String? = null,
        
        @ColumnInfo(name = "default_icon_res_id") 
        val defaultIconResId: Int? = null,
        
        @ColumnInfo(name = "default_icon_name") 
        val defaultIconName: String? = null,
        
        @ColumnInfo(name = "default_color") 
        val defaultColor: String? = null,
        
        @ColumnInfo(name = "created_at") 
        val createdAt: LocalDateTime = LocalDateTime.now(),
        
        @ColumnInfo(name = "updated_at") 
        val updatedAt: LocalDateTime = LocalDateTime.now(),
        // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
        @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
        @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)
