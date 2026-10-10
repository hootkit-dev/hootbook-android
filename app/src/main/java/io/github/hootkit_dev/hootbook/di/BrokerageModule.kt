package io.github.hootkit_dev.hootbook.di

import io.github.hootkit_dev.hootbook.data.brokerage.*
import io.github.hootkit_dev.hootbook.domain.brokerage.BrokerageProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BrokerageModule {
    @Binds abstract fun store(impl: EncryptedBrokerageStore): BrokerageStore
    @Binds @IntoSet abstract fun ibkr(impl: IbkrFlexProvider): BrokerageProvider
}
