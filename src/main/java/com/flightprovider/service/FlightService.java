package com.flightprovider.service;

import com.flightprovider.api.dto.BookingRequest;
import com.flightprovider.api.dto.BookingResponse;
import com.flightprovider.api.dto.FlightDto;
import com.flightprovider.domain.FlightStatuses;
import com.flightprovider.domain.entity.Flight;
import com.flightprovider.domain.entity.FlightBooking;
import com.flightprovider.repository.FlightBookingRepository;
import com.flightprovider.repository.FlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightBookingRepository bookingRepository;
    private final SeatService seatService;

    public FlightService(FlightRepository flightRepository,
                         FlightBookingRepository bookingRepository,
                         SeatService seatService) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
        this.seatService = seatService;
    }

    /**
     * Tim chuyen bay. Cac tham so deu optional (null = khong loc).
     * FP-09: loc o SQL thay vi findAll().stream().
     */
    @Transactional(readOnly = true)
    public List<FlightDto> search(String from, String to, LocalDate date, int passengers) {
        int minSeats = Math.max(passengers, 1);
        return flightRepository.search(from, to, date, minSeats).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<FlightDto> getById(Long id) {
        return flightRepository.findById(id).map(this::toDto);
    }

    /**
     * Dat nguyen chuyen (khong chon ghe cu the).
     *
     * <p>FP-05: tru ghe bang atomic conditional UPDATE; neu rowcount = 0 (het
     * ghe / bi giành mat) thi nem SeatSoldOutException -> 409. Khong con
     * read-check-write.
     *
     * <p>FP-01/FP-04: ghi {@code flight_bookings} CONFIRMED de availability
     * (duoc {@code SeatService.recomputeAvailable} doc lai qua SUM) khop voi so
     * ghe da tru -> hai duong khong dam nhau.
     */
    @Transactional
    public BookingResponse book(Long flightId, BookingRequest request) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));

        int seats = Math.max(request.seats(), 1);

        int updated = flightRepository.decrementAvailableSeats(flightId, seats);
        if (updated == 0) {
            throw new SeatSoldOutException("Not enough seats available");
        }

        FlightBooking booking = new FlightBooking();
        booking.setConfirmationCode(generateCode());
        booking.setFlightId(flight.getId());
        booking.setPassengerName(request.passengerName());
        booking.setContactEmail(request.contactEmail());
        booking.setSeats(seats);
        booking.setTotalPrice(flight.getBasePrice().multiply(BigDecimal.valueOf(seats)));
        booking.setStatus(FlightStatuses.BOOKING_CONFIRMED);
        bookingRepository.save(booking);

        return new BookingResponse(
                booking.getConfirmationCode(),
                flight.getId(),
                flight.getFlightNumber(),
                seats,
                booking.getTotalPrice(),
                flight.getCurrency(),
                booking.getStatus());
    }

    @Transactional
    public BookingResponse cancel(String confirmationCode) {
        FlightBooking booking = bookingRepository.findByConfirmationCode(confirmationCode)
                .orElseThrow(() -> new FlightNotFoundException("Booking not found: " + confirmationCode));

        Flight flight = flightRepository.findById(booking.getFlightId()).orElse(null);

        if (!FlightStatuses.BOOKING_CANCELLED.equals(booking.getStatus())) {
            if (booking.getHoldRef() != null) {
                // Seat-selection booking (FP-03): release its BOOKED seat rows and
                // let SeatService recompute the counter from the single source of truth.
                seatService.cancelSeatBooking(confirmationCode);
                booking = bookingRepository.findByConfirmationCode(confirmationCode).orElse(booking);
            } else {
                // Whole-flight booking: atomic give-back of the seat counter (FP-05 mirror).
                booking.setStatus(FlightStatuses.BOOKING_CANCELLED);
                bookingRepository.save(booking);
                flightRepository.incrementAvailableSeats(booking.getFlightId(), booking.getSeats());
            }
        }

        // FP-08: report the real flight number + currency, not null / hardcoded "VND".
        String flightNumber = flight != null ? flight.getFlightNumber() : null;
        String currency = flight != null ? flight.getCurrency() : "VND";

        return new BookingResponse(
                booking.getConfirmationCode(),
                booking.getFlightId(),
                flightNumber,
                booking.getSeats(),
                booking.getTotalPrice(),
                currency,
                booking.getStatus());
    }

    private FlightDto toDto(Flight f) {
        return new FlightDto(
                f.getId(),
                f.getFlightNumber(),
                f.getAirlineCode(),
                f.getFromAirport(),
                f.getToAirport(),
                f.getDepartureTime(),
                f.getArrivalTime(),
                f.getBasePrice(),
                f.getCurrency(),
                f.getAvailableSeats(),
                f.getAircraftType());
    }

    private String generateCode() {
        return "FL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
