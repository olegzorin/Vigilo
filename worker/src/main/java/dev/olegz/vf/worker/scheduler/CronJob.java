package dev.olegz.vf.worker.scheduler;

public interface CronJob extends Runnable {
    String name();

    String cron();

    default String timeZoneId() {
        return null;
    }

    /** Prevent overlapping executions of this job across all worker JVMs. */
    default boolean disallowConcurrent() {
        return true;
    }
}
