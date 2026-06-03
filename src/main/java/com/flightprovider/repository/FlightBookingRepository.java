package com.flightprovider.repository;

import com.flightprovider.domain.entity.FlightBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FlightBookingRepository extends JpaRepository<FlightBooking, Long> {
    Optional<FlightBooking> findByConfirmationCode(String confirmationCode);
}
