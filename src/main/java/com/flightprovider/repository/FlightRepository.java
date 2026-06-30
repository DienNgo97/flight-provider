package com.flightprovider.repository;

import com.flightprovider.domain.entity.Flight;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    /**
     * FP-09: push the search filter into SQL instead of {@code findAll().stream()}.
     * All parameters are optional (null = no filter on that column). The date
     * filter compares the DATE part of {@code departure_time}.
     */
    @Query("""
            select f from Flight f
            where (:from is null or upper(f.fromAirport) = upper(:from))
              and (:to is null or upper(f.toAirport) = upper(:to))
              and (:date is null or function('date', f.departureTime) = :date)
              and f.availableSeats >= :minSeats
            order by f.departureTime asc
            """)
    List<Flight> search(@Param("from") String from,
                        @Param("to") String to,
                        @Param("date") LocalDate date,
                        @Param("minSeats") int minSeats);

    /**
     * FP-06: pessimistic-write lock on the Flight row so concurrent seat
     * mutations that recompute availability serialise instead of clobbering
     * each other's counter (lost-update).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flight f where f.id = :id")
    Optional<Flight> findByIdForUpdate(@Param("id") Long id);

    /**
     * FP-05: atomic conditional decrement. Only succeeds (rowcount = 1) when
     * enough seats remain, so two concurrent {@code book()} calls for the last
     * seats cannot both pass. Returns the number of rows updated.
     */
    @Modifying
    @Query("update Flight f set f.availableSeats = f.availableSeats - :n " +
            "where f.id = :id and f.availableSeats >= :n")
    int decrementAvailableSeats(@Param("id") Long id, @Param("n") int n);

    /**
     * Atomic increment used when a whole-flight booking is cancelled, capped at
     * total_seats so the counter can never exceed capacity.
     */
    @Modifying
    @Query("update Flight f set f.availableSeats = " +
            "case when f.availableSeats + :n > f.totalSeats then f.totalSeats else f.availableSeats + :n end " +
            "where f.id = :id")
    int incrementAvailableSeats(@Param("id") Long id, @Param("n") int n);
}
