package com.flightprovider.repository;

import com.flightprovider.domain.entity.FlightBooking;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FlightBookingRepository extends JpaRepository<FlightBooking, Long> {

    Optional<FlightBooking> findByConfirmationCode(String confirmationCode);

    List<FlightBooking> findByHoldRef(String holdRef);

    List<FlightBooking> findByFlightIdAndStatus(Long flightId, String status);

    /**
     * FP-01: single source of truth helper. Sum of seats across CONFIRMED
     * whole-flight bookings for a flight. Combined with seat-row occupancy this
     * gives the true number of allocated seats so {@code flight_bookings} and
     * {@code flight_seats} can never clobber each other's view of availability.
     *
     * <p>{@code coalesce} guarantees 0 (never null) when there are no bookings.
     */
    @Query("select coalesce(sum(b.seats), 0) from FlightBooking b " +
            "where b.flightId = :flightId and b.status = 'CONFIRMED'")
    int sumConfirmedSeats(@Param("flightId") Long flightId);
}
