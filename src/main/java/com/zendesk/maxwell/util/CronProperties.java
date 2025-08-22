package com.zendesk.maxwell.util;

import static java.util.Objects.requireNonNullElse;
import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;

import com.codahale.metrics.Counter;
import com.zendesk.maxwell.MaxwellContext;
import com.zendesk.maxwell.row.HeartbeatRowMap;
import com.zendesk.maxwell.row.RowMap;

public class CronProperties {
    static final Logger LOGGER = getLogger(CronProperties.class);
    public static final int DEFAULT_VALUE = 0;

	private final int cronMaxHeartbeatsWithoutData;
	private final Runnable terminate;
	private final long limit;

	private int currentHeartbeatsWithoutData;

	private CronProperties(int cronMaxHeartbeatsWithoutData, int cronMaxSecondsRunning, Runnable terminate) {
		this.limit = cronMaxSecondsRunning == DEFAULT_VALUE ? DEFAULT_VALUE : System.currentTimeMillis() + cronMaxSecondsRunning * 1000;
		this.cronMaxHeartbeatsWithoutData = cronMaxHeartbeatsWithoutData;
		this.terminate = terminate;
	}

	public static CronProperties create(MaxwellContext context, Integer cronMaxHeartbeatsWithoutData, Integer cronMaxSecondsRunning) {
		final var cronMaxHeartbeatsWithoutDataValue = requireNonNullElse(cronMaxHeartbeatsWithoutData, DEFAULT_VALUE).intValue();
		final var cronMaxSecondsRunningValue = requireNonNullElse(cronMaxSecondsRunning, DEFAULT_VALUE).intValue();
		if (cronMaxHeartbeatsWithoutDataValue == DEFAULT_VALUE && cronMaxSecondsRunningValue == DEFAULT_VALUE)
			return null;

		return new CronProperties(cronMaxHeartbeatsWithoutData, cronMaxSecondsRunning, () -> {
			new Thread(() -> context.terminate()).start();
		});
	}

	public void checkTerminate(RowMap row, Counter counter) {

		if (limit != DEFAULT_VALUE && System.currentTimeMillis() > limit && terminate(counter, "cronMaxSecondsRunning"))
			return;

		if (cronMaxHeartbeatsWithoutData != DEFAULT_VALUE) {
			if (row instanceof HeartbeatRowMap) {
				currentHeartbeatsWithoutData++;
			} else if (row != null) {
				currentHeartbeatsWithoutData = 0;
			}

			if (currentHeartbeatsWithoutData == cronMaxHeartbeatsWithoutData && terminate(counter, "cronMaxHeartbeatsWithoutData"))
				return;
		}
	}

    private boolean terminate(Counter counter, String property) {
        LOGGER.info("Terminating due to {}: {} rows processed", property, counter.getCount());
        terminate.run();
        return true;
    }
}
