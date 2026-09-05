package org.bxteam.quark.update;

import org.bxteam.quark.common.SemanticVersion;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    private static UpdateProvider releasing(String version) {
        return () -> CompletableFuture.completedFuture(Optional.of(new ReleaseInfo(SemanticVersion.parse(version), null, null, null)));
    }

    @Test
    void reportsOutdated() throws Exception {
        UpdateChecker checker = new UpdateChecker(releasing("1.2.0"), "1.1.0");

        UpdateStatus.Outdated status = assertInstanceOf(UpdateStatus.Outdated.class, checker.check().get());

        assertEquals(SemanticVersion.parse("1.1.0"), status.current());
        assertEquals(SemanticVersion.parse("1.2.0"), status.latest().version());
        assertEquals(Optional.of(status), checker.lastStatus());
    }

    @Test
    void reportsUpToDateForSameOrOlderRelease() throws Exception {
        assertInstanceOf(UpdateStatus.UpToDate.class, new UpdateChecker(releasing("1.1.0"), "1.1.0").check().get());
        assertInstanceOf(UpdateStatus.UpToDate.class, new UpdateChecker(releasing("1.0.0"), "1.1.0").check().get());
        assertInstanceOf(UpdateStatus.UpToDate.class,
                new UpdateChecker(() -> CompletableFuture.completedFuture(Optional.empty()), "1.1.0").check().get());
    }

    @Test
    void failuresBecomeUnknown() throws Exception {
        UpdateCheckException cause = new UpdateCheckException("offline");

        UpdateStatus failed = new UpdateChecker(() -> CompletableFuture.failedFuture(cause), "1.0.0").check().get();
        UpdateStatus thrown = new UpdateChecker(() -> {
            throw cause;
        }, "1.0.0").check().get();

        assertSame(cause, assertInstanceOf(UpdateStatus.Unknown.class, failed).cause());
        assertSame(cause, assertInstanceOf(UpdateStatus.Unknown.class, thrown).cause());
    }

    @Test
    void lastStatusIsEmptyBeforeFirstCheck() {
        assertEquals(Optional.empty(), new UpdateChecker(releasing("1.0.0"), "1.0.0").lastStatus());
    }
}
