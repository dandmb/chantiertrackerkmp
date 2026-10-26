@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.dmb.chantiertracker.data.local

import com.dmb.chantiertracker.data.sync.SystemClock
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertTrue

class AttachmentPathIosTest {

    // ADR-41. VideoPlayer.ios.kt feeds attachment.localPath to AVPlayer via
    // NSURL.fileURLWithPath. Before the fix, that value was
    // PlatformFile.absolutePath() == nsUrl.absoluteString, i.e. a "file://…" URL
    // string; NSURL.fileURLWithPath treats it as a *plain path* and double-wraps
    // it ("file:///…/data/file:/…/clip.mp4"), so AVPlayer never finds the file.
    //
    // After the fix the store keeps a bare key and resolves it fresh to a real
    // filesystem path, which NSURL.fileURLWithPath handles correctly.
    @Test
    fun the_saved_video_path_resolves_for_AVPlayer() = runTest {
        val store = FileKitAttachmentFileStore(newFileName = { "iospathtest-${NSUUID().UUIDString}" })
        val key = store.save(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), "clip.mp4")
        val path = store.absolutePathOf(key)

        try {
            assertTrue(!key.contains('/'), "the stored key '$key' must be a bare file name, not a path")

            // Exactly what VideoPlayer.ios.kt hands to AVPlayer:
            val playerUrl = NSURL.fileURLWithPath(path)
            assertTrue(
                playerUrl.checkResourceIsReachableAndReturnError(null),
                "key='$key' path='$path' -> '${playerUrl.absoluteString}' does not reach the saved file",
            )

            // The bytes are actually there via the key too.
            assertTrue(store.readBytes(key).size == 8)
        } finally {
            store.delete(key)
        }
    }

    @Test
    fun the_stored_files_list_every_saved_file_by_its_bare_key_with_the_time_it_was_written() = runTest {
        val store = FileKitAttachmentFileStore(newFileName = { "ioskeystest-${NSUUID().UUIDString}" })
        val photo = store.save(byteArrayOf(1, 2, 3), "photo.jpg")
        val clip = store.save(byteArrayOf(4, 5, 6), "clip.mp4")

        try {
            val listed = store.storedFiles()
            assertTrue(listed.map { it.key }.containsAll(listOf(photo, clip)), "saved '$photo' and '$clip', listed $listed")
            val now = SystemClock.nowEpochMillis()
            listed.filter { it.key == photo || it.key == clip }.forEach {
                assertTrue(it.lastModifiedEpochMillis in (now - 60_000L)..(now + 60_000L), "${it.key} written at ${it.lastModifiedEpochMillis}, now $now")
            }
            store.delete(photo)
            val left = store.storedFiles().map { it.key }
            assertTrue(photo !in left && clip in left)
        } finally {
            store.delete(photo)
            store.delete(clip)
        }
    }
}
