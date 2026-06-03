package com.flightprovider.service;

import com.flightprovider.api.dto.BookingRequest;
import com.flightprovider.api.dto.BookingResponse;
import com.flightprovider.api.dto.FlightDto;
import com.flightprovider.domain.entity.Flight;
import com.flightprovider.domain.entity.FlightBooking;
import com.flightprovider.repository.FlightBookingRepository;
import com.flightprovider.repository.FlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightBookingRepository bookingRepository;

    public FlightService(FlightRepository flightRepository, FlightBookingRepository bookingRepository) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Tim chuyen bay. Cac tham so deu optional (null = khong loc).
     * Du lieu mock nho nen loc in-memory cho don gian.
     */
    @Transactional(readOnly = true)
    public List<FlightDto> search(String from, String to, LocalDate date, int passengers) {
        int minSeats = Math.max(passengers, 1);
        return flightRepository.findAll().stream()
                .filter(f -> from == null || f.getFromAirport().equalsIgnoreCase(from))
                .filter(f -> to == null || f.getToAirport().equalsIgnoreCase(to))
                .filter(f -> date == null || f.getDepartureTime().toLocalDate().equals(date))
                .filter(f -> f.getAvailableSeats() >= minSeats)
                .sorted(Comparator.comparing(Flight::getDepartureTime))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<FlightDto> getById(Long id) {
        return flightRepository.findById(id).map(this::toDto);
    }

    @Transactional
    public BookingResponse book(Long flightId, BookingRequest request) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new IllegalArgumentException("Flight not found: " + flightId));

        int seats = Math.max(request.seats(), 1);
        if (flight.getAvailableSeats() < seats) {
            throw new IllegalStateException("Not enough seats available");
        }

        flight.setAvailableSeats(flight.getAvailableSeats() - seats);
        flightRepository.save(flight);

        FlightBooking booking = new FlightBooking();
        booking.setConfirmationCode(generateCode());
        booking.setFlightId(flight.getId());
        booking.setPassengerName(request.passengerName());
        booking.setContactEmail(request.contactEmail());
        booking.setSeats(seats);
        booking.setTotalPrice(flight.getBasePrice().multiply(BigDecimal.valueOf(seats)));
        booking.setStatus("CONFIRMED");
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
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + confirmationCode));

        if (!"CANCELLED".equals(booking.getStatus())) {
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);
            // tra ghe lai cho chuyen bay
            flightRepository.findById(booking.getFlightId()).ifPresent(flight -> {
                flight.setAvailableSeats(flight.getAvailableSeats() + booking.getSeats());
                flightRepository.save(flight);
            });
        }

        return new BookingResponse(
                booking.getConfirmationCode(),
                booking.getFlightId(),
                null,
                booking.getSeats(),
                booking.getTotalPrice(),
                "VND",
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
