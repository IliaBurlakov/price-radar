package com.priceradar.scheduler.application;

import java.time.Duration;

public interface ScheduleJitter {

    Duration next();
}
