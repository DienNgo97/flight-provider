package com.flightprovider.repository;

import com.flightprovider.domain.entity.FlightSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {

    List<FlightSeat> findByFlightId(Long flightId);

    List<FlightSeat> findByFlightIdAndHoldRef(Long flightId, String holdRef);

    /** Cac ghe dang giu (HELD) da het han -> can nha ra. */
    List<FlightSeat> findByStatusAndExpiresAtBefore(String status, Instant t);
}
