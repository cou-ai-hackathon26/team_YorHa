package com.medidiary.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncCoreTest {
    private val record = SharedRecord(
        uuid = "rec-1234-abcd", sessionUuid = "ses-9", sessionTimestamp = 1_700_000_000_000,
        type = "medicine", originalName = "নাপা", genericSuggestion = "Paracetamol", genericMatchStatus = "EXACT",
        timePhrase = "সকালে", note = null, loggedDate = 1_700_000_000_500, authorName = "Ammu",
        authorDevice = "dev-a", updatedAt = 1_700_000_001_000, updatedBy = "dev-a", deleted = false,
    )

    @Test fun cryptoRoundTrip_andCiphertextIsRandomised() {
        val key = SyncCrypto.newKey()
        val a = SyncCrypto.encrypt(key, "hello নাপা", "aad")
        val b = SyncCrypto.encrypt(key, "hello নাপা", "aad")
        assertNotEquals(a, b)
        assertEquals("hello নাপা", SyncCrypto.decrypt(key, a, "aad"))
    }

    @Test fun wrongKeyOrAadFails() {
        val key = SyncCrypto.newKey()
        val c = SyncCrypto.encrypt(key, "secret", "aad")
        listOf<() -> Unit>(
            { SyncCrypto.decrypt(SyncCrypto.newKey(), c, "aad") },
            { SyncCrypto.decrypt(key, c, "other") },
        ).forEach {
            try { it(); fail("should not decrypt") } catch (_: Exception) {}
        }
    }

    @Test fun envelopeHidesContent_andIsBoundToDocId() {
        val key = SyncCrypto.newKey()
        val e = Envelope.seal(key, "diary-1234", record)
        assertFalse(e.cipher.contains("Paracetamol"))
        assertEquals(record.updatedAt, e.updatedAt)
        assertEquals(record, Envelope.open(key, "diary-1234", record.uuid, e))
        try { Envelope.open(key, "diary-1234", "rec-other-id", e); fail() } catch (_: Exception) {}
        try { Envelope.open(key, "diary-9999", record.uuid, e); fail() } catch (_: Exception) {}
    }

    @Test fun recordJsonRoundTrip_withNulls() {
        val r = record.copy(genericSuggestion = null, timePhrase = null, authorName = null, deleted = true)
        assertEquals(r, SharedRecord.fromJson(r.toJson()))
    }

    @Test fun inviteRoundTrip_andParseFromSharedMessage() {
        val inv = Invite("0f8a2c1e-5b7d-4e2a-9c3f-123456789abc", SyncCrypto.newKey(), "Ammu's diary")
        val link = inv.toLink()
        assertTrue(link.startsWith("medidiary://join?"))
        assertEquals(inv, Invite.parse(link))
        assertEquals(inv, Invite.parse("Join my MediDiary: $link (keep this private)"))
    }

    @Test fun badInvitesAreRejected() {
        assertNull(Invite.parse("hello"))
        assertNull(Invite.parse("medidiary://join?d=short&k=AAAA"))
        assertNull(Invite.parse("medidiary://join?d=0f8a2c1e-5b7d&k=not-a-key"))
    }

    @Test fun lastWriterWins_withDeterministicTieBreak() {
        val local = MergePolicy.Stamp(100, "dev-a")
        assertTrue(MergePolicy.remoteWins(null, MergePolicy.Stamp(1, "x")))
        assertTrue(MergePolicy.remoteWins(local, MergePolicy.Stamp(101, "dev-a")))
        assertFalse(MergePolicy.remoteWins(local, MergePolicy.Stamp(99, "dev-z")))
        // Same time: the higher device id wins on BOTH phones, so they converge.
        assertTrue(MergePolicy.remoteWins(local, MergePolicy.Stamp(100, "dev-b")))
        assertFalse(MergePolicy.remoteWins(MergePolicy.Stamp(100, "dev-b"), local))
        // Our own write echoed back by the listener is ignored.
        assertFalse(MergePolicy.remoteWins(local, local))
    }
}
