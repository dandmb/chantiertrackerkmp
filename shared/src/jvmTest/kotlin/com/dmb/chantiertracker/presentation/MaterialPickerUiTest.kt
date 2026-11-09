package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.logs.MATERIAL_SUGGESTIONS_TAG
import com.dmb.chantiertracker.presentation.logs.PurchaseLineFormScreen
import com.dmb.chantiertracker.presentation.logs.PurchaseLineFormViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeMaterialRepository
import com.dmb.chantiertracker.support.FakePurchaseLineRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MaterialPickerUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun ComposeUiTest.showForm(locale: String = "fr"): PurchaseLineFormViewModel {
        val materials = FakeMaterialRepository(
            materials = listOf(Material("m1", "1", "Ciment", "sac"), Material("m2", "1", "Ciment blanc", "sac"), Material("m3", "1", "Fer", "barre")),
        )
        val vm = PurchaseLineFormViewModel(materials, FakePurchaseLineRepository()).also { it.load("e1", "1", null) }
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        PurchaseLineFormScreen("e1", "1", null, "EUR", onSaved = {}, onBack = {}, viewModel = vm)
                    }
                }
            }
        }
        waitForIdle()
        return vm
    }

    private fun ComposeUiTest.inMenu(text: String) = onNode(hasText(text) and hasAnyAncestor(hasTestTag(MATERIAL_SUGGESTIONS_TAG)))

    @Test
    fun typing_opens_a_menu_of_existing_materials_titled_with_their_unit_and_a_last_create_option() = runComposeUiTest {
        showForm()
        onNodeWithText("Matériau").performTextInput("cim")
        waitForIdle()

        inMenu("Matériaux existants").assertExists()
        inMenu("Ciment").assertExists()
        inMenu("Ciment blanc").assertExists()
        assertEquals(2, onAllNodes(hasText("sac") and hasAnyAncestor(hasTestTag(MATERIAL_SUGGESTIONS_TAG))).fetchSemanticsNodes().size)
        inMenu("Créer « cim »").assertExists()
        inMenu("Fer").assertDoesNotExist()

        val texts = onAllNodes(hasAnyAncestor(hasTestTag(MATERIAL_SUGGESTIONS_TAG)) and hasText("", substring = true))
            .fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { part -> part.text } }
        assertEquals("Créer « cim »", texts.last(), "the create option comes last")
    }

    @Test
    fun picking_a_suggestion_fills_the_field_and_closes_the_menu() = runComposeUiTest {
        val vm = showForm()
        onNodeWithText("Matériau").performTextInput("fe")
        waitForIdle()
        inMenu("Fer").performClick()
        waitForIdle()

        assertEquals("m3", vm.state.value.selectedMaterialId)
        check(onAllNodes(hasAnyAncestor(hasTestTag(MATERIAL_SUGGESTIONS_TAG))).fetchSemanticsNodes().isEmpty()) { "menu closed" }
    }

    @Test
    fun the_create_option_asks_for_the_unit_of_the_new_material() = runComposeUiTest {
        val vm = showForm()
        onNodeWithText("Matériau").performTextInput("Gravier")
        waitForIdle()
        inMenu("Créer « Gravier »").performClick()
        waitForIdle()

        assertEquals("", vm.state.value.creatingNewUnit)
        onNodeWithText("Unité").assertExists()
    }

    @Test
    fun in_english_the_menu_reads_existing_materials() = runComposeUiTest {
        showForm(locale = "en")
        onNodeWithText("Material").performTextInput("ci")
        waitForIdle()

        inMenu("Existing materials").assertExists()
        inMenu("Create \"ci\"").assertExists()
    }

    @Test
    fun touching_quantity_while_the_suggestions_are_open_moves_into_quantity() = runComposeUiTest {
        val vm = showForm()
        onNodeWithText("Matériau").performTextInput("Gravier")
        waitForIdle()
        inMenu("Créer « Gravier »").assertExists()

        onNode(hasText("Quantité", substring = true) and androidx.compose.ui.test.hasSetTextAction()).performClick()
        waitForIdle()
        onNode(hasText("Quantité", substring = true) and androidx.compose.ui.test.hasSetTextAction()).performTextInput("10")
        waitForIdle()

        assertEquals(null, vm.state.value.creatingNewUnit, "the create option was not triggered")
        assertEquals("Gravier", vm.state.value.materialQuery)
        assertEquals("10", vm.state.value.quantity)
        onNode(hasText("Quantité", substring = true) and androidx.compose.ui.test.hasSetTextAction()).assertIsFocused()
    }
}
