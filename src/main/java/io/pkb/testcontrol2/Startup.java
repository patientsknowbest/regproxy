package io.pkb.testcontrol2;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Applications should send this to test-support on startup to register themselves for test control calls.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableStartup.class)
@JsonDeserialize(as = ImmutableStartup.class)
public interface Startup {
    /**
     * Human-readable name for the application
     */
    String name();

    /**
     * callback URL on which test control requests will be received
     */
    String callback();
}
