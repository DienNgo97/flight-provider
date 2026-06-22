package com.flightprovider.scheduler;

import com.flightprovider.service.SeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moi 60 giay: nha cac ghe dang GIU (HELD) da qua thoi gian giu (mac dinh 20 phut)
 * -> ghe tu dong tro ve trang thai trong cho khach khac dat.
 */
@Component
public class SeatReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatReleaseScheduler.class);

    private final SeatService seatService;

    public SeatReleaseScheduler(SeatService seatService) {
        this.seatService = seatService;
    }

    @Scheduled(fixedRate = 60_000)
    public void releaseExpiredHolds() {
        int released = seatService.releaseExpired();
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
    }
}
