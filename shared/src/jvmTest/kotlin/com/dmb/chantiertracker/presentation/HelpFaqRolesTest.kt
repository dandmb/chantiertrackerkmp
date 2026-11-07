package com.dmb.chantiertracker.presentation

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.help_a_admin
import com.dmb.chantiertracker.resources.help_a_owner
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HelpFaqRolesTest {

    @AfterTest fun tearDown() { customAppLocale = null }

    private fun read(locale: String, res: StringResource): String {
        var text = ""
        runComposeUiTest {
            setContent {
                customAppLocale = locale
                AppEnvironment { Text(stringResource(res), Modifier.testTag("faq")) }
            }
            text = onNodeWithTag("faq").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        }
        return text
    }

    @Test
    fun the_faq_says_only_the_owner_removes_a_member() {
        assertTrue(read("fr", Res.string.help_a_owner).contains("lui seul peut retirer un membre"), read("fr", Res.string.help_a_owner))
        assertTrue(read("en", Res.string.help_a_owner).contains("only they can remove a member"), read("en", Res.string.help_a_owner))
    }

    @Test
    fun the_faq_no_longer_lets_an_admin_believe_they_manage_the_members() {
        assertFalse(read("fr", Res.string.help_a_admin).contains("gère les membres"))
        assertFalse(read("en", Res.string.help_a_admin).contains("manages the project's members"))
        assertTrue(read("fr", Res.string.help_a_admin).contains("seul le propriétaire"), read("fr", Res.string.help_a_admin))
        assertTrue(read("en", Res.string.help_a_admin).contains("only the owner"), read("en", Res.string.help_a_admin))
    }
}
