package com.metanet4j.sdk.signers;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.base.bsv.BsvHashBytes;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class AllHashSigThreadLocalContractTest {

    @Test
    void findsMatchingSigningContextAndRejectsUnknownHash() {
        AllHashSigThreadLocal store = new AllHashSigThreadLocal();
        BsvHashBytes firstHash = BsvHashBytes.of(new byte[] {1});
        BsvHashBytes secondHash = BsvHashBytes.of(new byte[] {2});
        PreSignHashContext first = new PreSignHashContext();
        first.setPreSignHash(firstHash);
        PreSignHashContext second = new PreSignHashContext();
        second.setPreSignHash(secondHash);

        assertTrue(store.get().isEmpty());
        store.get().add(first);
        store.get().add(second);
        assertSame(second, store.getSignedHashContext(secondHash));
        assertSame(first, store.getSignedHashContext(firstHash));
        RuntimeException missing = assertThrows(RuntimeException.class,
                () -> store.getSignedHashContext(BsvHashBytes.of(new byte[] {3})));
        assertTrue(missing.getMessage().contains("get preSignHashContext is null"));
        store.remove();
    }

    @Test
    void keepsSigningContextsSeparateAcrossThreads() throws InterruptedException {
        AllHashSigThreadLocal store = new AllHashSigThreadLocal();
        BsvHashBytes hash = BsvHashBytes.of(new byte[] {4});
        PreSignHashContext context = new PreSignHashContext();
        context.setPreSignHash(hash);
        store.get().add(context);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                assertTrue(store.get().isEmpty());
                assertThrows(RuntimeException.class, () -> store.getSignedHashContext(hash));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                store.remove();
            }
        });
        other.start();
        other.join();

        assertNull(failure.get());
        assertSame(context, store.getSignedHashContext(hash));
        store.remove();
    }
}
