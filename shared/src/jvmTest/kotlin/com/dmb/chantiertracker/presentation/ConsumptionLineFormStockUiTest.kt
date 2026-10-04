package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormContent
import com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormUiState
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ConsumptionLineFormStockUiTest {

    @AfterTest fun tearDown() { customAppLocale = null }

    private val stock = listOf(
        MaterialStock("m1", "Ciment", "sac", quantityIn = 12.0, quantityOut = 0.0),
        MaterialStock("m2", "Fer", "barre", quantityIn = 0.0, quantityOut = 0.0),
    )

    private fun mount(state: ConsumptionLineFormUiState, locale: String = "fr", check: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    ConsumptionLineFormContent(state, onSelectMaterial = {}, onQuantityChange = {}, onSave = {}, onBack = {})
                }
            }
        }
        waitForIdle()
        check()
    }

    @Test
    fun a_loaded_stock_shows_when_it_dates_from_and_what_is_available() =
        mount(ConsumptionLineFormUiState(ready = true, stock = stock, stockRefreshedAt = 1_700_000_000_000L)) {
            onNodeWithText("Stock au ", substring = true).assertExists()
            onNodeWithText("Disponible : 12 sac").assertExists()
            onNodeWithText("Fer (barre)").assertDoesNotExist()
            onNodeWithText("Stock non vérifié hors ligne", substring = true).assertDoesNotExist()
        }

    @Test
    fun a_stock_never_loaded_warns_and_offers_every_material_without_a_false_zero() =
        mount(ConsumptionLineFormUiState(ready = true, stock = stock, stockRefreshedAt = null)) {
            onNodeWithText("Stock non vérifié hors ligne : le serveur validera à l'envoi").assertExists()
            onNodeWithText("Fer (barre)").assertExists()
            assertEquals(2, onAllNodesWithText("Stock pas encore chargé").fetchSemanticsNodes().size)
            onNodeWithText("Disponible", substring = true).assertDoesNotExist()
            onNodeWithText("Stock au ", substring = true).assertDoesNotExist()
        }

    @Test
    fun the_warning_is_translated() =
        mount(ConsumptionLineFormUiState(ready = true, stock = stock, stockRefreshedAt = null), locale = "en") {
            onNodeWithText("Stock not verified offline: the server will check it when sending").assertExists()
        }
}
