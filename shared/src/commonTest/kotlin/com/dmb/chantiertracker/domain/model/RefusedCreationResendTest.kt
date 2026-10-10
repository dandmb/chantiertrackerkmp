package com.dmb.chantiertracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefusedCreationResendTest {

    private val sentAgain = true
    private val frozen = false

    private val expectedByReason = mapOf(
        RefusalReason.PLAN_LIMIT to sentAgain,
        RefusalReason.PROJECT_OR_STAGE_INACTIVE to sentAgain,
        RefusalReason.INSUFFICIENT_ROLE to sentAgain,
        RefusalReason.INSUFFICIENT_STOCK to sentAgain,
        RefusalReason.INVALID_VALUE to frozen,
        RefusalReason.DUPLICATE_ENTRY to frozen,
        RefusalReason.DUPLICATE_MATERIAL to frozen,
        RefusalReason.ENTRY_DATE_RESTRICTED to frozen,
        RefusalReason.STOCK_CONSUMED to frozen,
        RefusalReason.FILE_REFUSED to frozen,
        RefusalReason.UNKNOWN to frozen,
    )

    private val expectedByCode = mapOf(
        "PLAN_LIMIT_EXCEEDED" to sentAgain,
        "PROJECT_OR_STAGE_INACTIVE" to sentAgain,
        "PROJECT_INSUFFICIENT_ROLE" to sentAgain,
        "INSUFFICIENT_STOCK" to sentAgain,
        "ENTRY_DATE_RESTRICTED" to frozen,
        "STOCK_CONSUMED" to frozen,
        "STOCK_RELEASE_BLOCKED" to frozen,
        "DUPLICATE_ENTRY" to frozen,
        "DUPLICATE_MATERIAL" to frozen,
        "VALIDATION_FAILED" to frozen,
        "INVALID_AMOUNT" to frozen,
        "MATERIAL_PROJECT_MISMATCH" to frozen,
        "ENTRY_TYPE_MISMATCH" to frozen,
        "ATTACHMENT_TOO_LARGE" to frozen,
        "INVALID_ATTACHMENT_TYPE" to frozen,
        "INVALID_ATTACHMENT_NAME" to frozen,
        "UNSUPPORTED_IMAGE" to frozen,
        "UNREADABLE_VIDEO" to frozen,
        "VIDEO_TOO_LONG" to frozen,
        "LOCAL_FILE_MISSING" to frozen,
    )

    @Test
    fun every_reason_is_classified_as_sent_again_or_frozen_until_corrected() {
        assertEquals(RefusalReason.entries.toSet(), expectedByReason.keys, "a reason added to the enum must be classified here")
        RefusalReason.entries.forEach { reason ->
            assertEquals(expectedByReason.getValue(reason), reason.creationIsSentAgainByEachPass, "$reason")
        }
    }

    @Test
    fun every_code_the_app_knows_is_classified_and_a_new_code_must_be_added_to_this_table() {
        assertEquals(knownRefusalCodes.keys, expectedByCode.keys, "a server code added to the app without a classification here")
        expectedByCode.forEach { (code, expected) ->
            assertEquals(expected, refusalReasonOf(code).creationIsSentAgainByEachPass, code)
        }
    }

    @Test
    fun a_code_the_app_does_not_know_and_a_refusal_kept_without_a_code_are_frozen() {
        listOf("A_CODE_FROM_A_NEWER_SERVER", "", null).forEach { code ->
            assertEquals(RefusalReason.UNKNOWN, refusalReasonOf(code), "$code")
            assertFalse(refusalReasonOf(code).creationIsSentAgainByEachPass, "$code")
        }
    }

    @Test
    fun only_a_refused_creation_can_wait_for_a_correction() {
        RefusalReason.entries.forEach { reason ->
            assertEquals(!expectedByReason.getValue(reason), SyncIssue(SyncIssueKind.REFUSED, reason).waitsForACorrection, "$reason, creation")
            listOf(SyncIssueKind.UPDATE_REFUSED, SyncIssueKind.DELETE_REFUSED, SyncIssueKind.DELETED_ON_SERVER, SyncIssueKind.BLOCKED_BY_PARENT).forEach { kind ->
                assertFalse(SyncIssue(kind, reason).waitsForACorrection, "$kind, $reason: this rule is about refused creations only")
            }
        }
        assertTrue(SyncIssue(SyncIssueKind.REFUSED, reason = null).waitsForACorrection, "a refused creation with no reason at all is frozen like an unknown one")
        assertFalse(SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT).waitsForACorrection, "a child waiting on its parent is not refused")
    }

    @Test
    fun what_the_user_can_retry_is_still_sent_again_by_itself() {
        RefusalReason.entries.filter { it.dependsOnSomethingElse }.forEach { reason ->
            assertTrue(reason.creationIsSentAgainByEachPass, "$reason offers Retry, so it is not frozen")
        }
    }
}
