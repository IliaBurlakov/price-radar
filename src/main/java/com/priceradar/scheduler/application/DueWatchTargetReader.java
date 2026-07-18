package com.priceradar.scheduler.application;

import java.time.Instant;
import java.util.List;

public interface DueWatchTargetReader {

    List<DueWatchTarget> findDue(Instant now, int limit);
}
