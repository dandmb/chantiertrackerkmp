package com.dmb.chantiertracker.data.session

import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.ExportFileStore
import com.dmb.chantiertracker.data.local.db.LocalDataDao
import com.dmb.chantiertracker.di.dataModule
import com.dmb.chantiertracker.presentation.billing.CheckoutDeepLinkDispatcher
import com.dmb.chantiertracker.presentation.invitations.InvitationDeepLinkDispatcher
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeExportFileStore
import com.dmb.chantiertracker.support.FakeLocalDataDao
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountSwitchInvitationLinkTest {

    private val invitations = InvitationDeepLinkDispatcher()
    private val checkout = CheckoutDeepLinkDispatcher()

    private val koin = startKoin {
        allowOverride(true)
        modules(
            dataModule,
            module {
                single<LocalDataDao> { FakeLocalDataDao() }
                single<AttachmentFileStore> { FakeAttachmentFileStore() }
                single<ExportFileStore> { FakeExportFileStore() }
                single { SyncStateHolder() }
                single { checkout }
                single { invitations }
            },
        )
    }.koin

    @AfterTest fun tearDown() = stopKoin()

    @Test
    fun the_invitation_link_opened_while_signed_out_survives_the_erase_of_the_previous_account() = runTest {
        invitations.dispatch("jeton-42")

        koin.get<LocalDataEraser>().eraseAll()

        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun the_erase_still_drops_a_pending_payment_return() = runTest {
        checkout.dispatch("chantiertracker://checkout-success")

        koin.get<LocalDataEraser>().eraseAll()

        assertNull(checkout.pending.value)
    }
}
