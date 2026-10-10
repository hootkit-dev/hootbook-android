package io.github.hootkit_dev.hootbook.data.repository

import io.github.hootkit_dev.hootbook.data.database.dao.CardDao
import io.github.hootkit_dev.hootbook.data.database.entity.CardEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CardType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(
    private val cardDao: CardDao
) {
    
    suspend fun insertCard(card: CardEntity): Long {
        return cardDao.insertCard(card)
    }
    
    suspend fun updateCard(card: CardEntity) {
        cardDao.updateCard(card.copy(updatedAt = LocalDateTime.now()))
    }
    
    suspend fun deleteCard(card: CardEntity) {
        cardDao.deleteCard(card)
    }
    
    suspend fun deleteCard(cardId: Long) {
        val card = cardDao.getCardById(cardId)
        if (card != null) {
            cardDao.deleteCard(card)
        }
    }
    
    suspend fun getCard(bankName: String, cardLast4: String): CardEntity? {
        return cardDao.getCard(bankName, cardLast4)
    }
    
    suspend fun getCardById(cardId: Long): CardEntity? {
        return cardDao.getCardById(cardId)
    }

    suspend fun getCardsForAccount(accountLast4: String): List<CardEntity> {
        return cardDao.getCardsForAccount(accountLast4)
    }
    
    fun getCardsForAccountFlow(accountLast4: String): Flow<List<CardEntity>> {
        return cardDao.getCardsForAccountFlow(accountLast4)
    }

    fun getAllActiveCards(): Flow<List<CardEntity>> {
        return cardDao.getAllActiveCards()
    }
    
    fun getAllCards(): Flow<List<CardEntity>> {
        return cardDao.getAllCards()
    }
    
    suspend fun linkCardToAccount(cardId: Long, accountLast4: String?) {
        cardDao.linkCardToAccount(cardId, accountLast4)
    }
    
    suspend fun unlinkCard(cardId: Long) {
        cardDao.linkCardToAccount(cardId, null)
    }
    
    suspend fun setCardActive(cardId: Long, isActive: Boolean) {
        cardDao.setCardActive(cardId, isActive)
    }
    
    suspend fun getCardCount(): Int {
        return cardDao.getCardCount()
    }
    
    suspend fun getCardCountByType(cardType: CardType): Int {
        return cardDao.getCardCountByType(cardType)
    }
    
    suspend fun getBankCards(bankName: String, cardType: CardType): List<CardEntity> {
        return cardDao.getBankCards(bankName, cardType)
    }

    suspend fun deleteAllCards() {
        cardDao.deleteAllCards()
    }
}