package cn.rukkit.network;

import java.util.concurrent.TimeUnit;

/** Shared timing constants for the Rusted Warfare network simulation tick. */
public final class NetworkTick {
    /** The original protocol advances ten simulation frames per network tick. */
    public static final int FRAMES_PER_WINDOW = 10;

    /**
     * Ten frames at 60 simulation frames per second. Nanoseconds keep the
     * schedule accurate without millisecond rounding drift.
     */
    public static final long WINDOW_PERIOD_NANOS = TimeUnit.SECONDS.toNanos(1) / 6;

    /** Human-readable approximation for existing diagnostics. */
    public static final int WINDOW_PERIOD_MILLIS = 167;

    private NetworkTick() {}
}
