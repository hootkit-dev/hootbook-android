package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "categories",
    indices = [Index(value = ["sync_id"]), Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "color")
    val color: String,

    @ColumnInfo(name = "icon_res_id", defaultValue = "0")
    val iconResId: Int = 0,
    
    @ColumnInfo(name = "icon_name", defaultValue = "")
    val iconName: String = "",
    
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",
    
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,
    
    @ColumnInfo(name = "is_income")
    val isIncome: Boolean = false,
    
    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 999,
    
    // Default values for reset functionality (null for user-created categories)
    @ColumnInfo(name = "default_name")
    val defaultName: String? = null,
    
    @ColumnInfo(name = "default_color")
    val defaultColor: String? = null,
    
    @ColumnInfo(name = "default_icon_res_id")
    val defaultIconResId: Int? = null,
    
    @ColumnInfo(name = "default_icon_name")
    val defaultIconName: String? = null,
    
    @ColumnInfo(name = "default_description")
    val defaultDescription: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
    @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
    @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)