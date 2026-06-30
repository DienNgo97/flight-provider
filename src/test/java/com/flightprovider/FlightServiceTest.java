package com.flightprovider;

import com.flightprovider.api.dto.BookingRequest;
import com.flightprovider.api.dto.BookingResponse;
import com.flightprovider.api.dto.FlightDto;
import com.flightprovider.domain.FlightStatuses;
import com.flightprovider.domain.entity.Flight;
import com.flightprovider.domain.entity.FlightBooking;
import com.flightprovider.repository.FlightBookingRepository;
import com.flightprovider.repository.FlightRepository;
import com.flightprovider.service.FlightService;
import com.flightprovider.service.SeatService;
import com.flightprovider.service.SeatSoldOutException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightServiceTest {

    private Flight flight(String from, String to, String time, int seats) {
        Flight f = new Flight();
        f.setId(1L);
        f.setFlightNumber("VN001");
        f.setAirlineCode("VN");
        f.setFromAirport(from);
        f.setToAirport(to);
        f.setDepartureTime(LocalDateTime.parse(time));
        f.setArrivalTime(LocalDateTime.parse(time).plusHours(2));
        f.setBasePrice(new BigDecimal("1500000"));
        f.setCurrency("VND");
        f.setTotalSeats(180);
        f.setAvailableSeats(seats);
        f.setAircraftType("A321");
        return f;
    }

    @Test
    void searchFiltersByRoute() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);
        SeatService seatService = mock(SeatService.class);
        // FP-09: search now delegates the filter to the repository @Query.
        when(flightRepo.search("HAN", "SGN", null, 1)).thenReturn(List.of(
                flight("HAN", "SGN", "2026-07-01T06:00:00", 180)
        ));
        FlightService service = new FlightService(flightRepo, bookingRepo, seatService);

        List<FlightDto> result = service.search("HAN", "SGN", null, 1);

        assertEquals(1, result.size());
        assertEquals("SGN", result.get(0).to());
    }

    @Test
    void bookUsesAtomicDecrementAndPersistsConfirmedBooking() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);
        SeatService seatService = mock(SeatService.class);
        Flight f = flight("HAN", "SGN", "2026-07-01T06:00:00", 180);
        when(flightRepo.findById(1L)).thenReturn(Optional.of(f));
        // FP-05: the atomic conditional UPDATE succeeds (one row changed).
        when(flightRepo.decrementAvailableSeats(1L, 2)).thenReturn(1);

        FlightService service = new FlightService(flightRepo, bookingRepo, seatService);
        BookingResponse resp = service.book(1L, new BookingRequest("Jay", "jay@x.com", 2));

        // Decrement is the source of mutation - no read-check-write setAvailableSeats.
        verify(flightRepo).decrementAvailableSeats(1L, 2);
        verify(flightRepo, never()).save(any(Flight.class));
        verify(bookingRepo).save(any(FlightBooking.class));
        assertEquals(2, resp.seats());
        assertEquals("VN001", resp.flightNumber());
        assertEquals("VND", resp.currency());
        assertEquals(FlightStatuses.BOOKING_CONFIRMED, resp.status());
    }

    @Test
    void bookReturns409WhenSoldOut() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);
        SeatService seatService = mock(SeatService.class);
        Flight f = flight("HAN", "SGN", "2026-07-01T06:00:00", 1);
        when(flightRepo.findById(1L)).thenReturn(Optional.of(f));
        // FP-05: conditional UPDATE matched no rows -> lost the race / sold out.
        when(flightRepo.decrementAvailableSeats(eq(1L), anyInt())).thenReturn(0);

        FlightService service = new FlightService(flightRepo, bookingRepo, seatService);

        // SeatSoldOutException is mapped to HTTP 409 by the controller.
        assertThrows(SeatSoldOutException.class,
                () -> service.book(1L, new BookingRequest("Jay", "jay@x.com", 2)));
        verify(bookingRepo, never()).save(any(FlightBooking.class));
    }

    @Test
    void cancelResponseCarriesFlightNumberAndCurrency() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);
        SeatService seatService = mock(SeatService.class);

        FlightBooking booking = new FlightBooking();
        booking.setConfirmationCode("FLABCDEF12");
        booking.setFlightId(1L);
        booking.setSeats(2);
        booking.setTotalPrice(new BigDecimal("3000000"));
        booking.setStatus(FlightStatuses.BOOKING_CONFIRMED);
        // whole-flight booking (no holdRef) -> counter give-back path.

        Flight f = flight("HAN", "SGN", "2026-07-01T06:00:00", 178);
        f.setFlightNumber("VN999");
        f.setCurrency("USD");

        when(bookingRepo.findByConfirmationCode("FLABCDEF12")).thenReturn(Optional.of(booking));
        when(flightRepo.findById(1L)).thenReturn(Optional.of(f));

        FlightService service = new FlightService(flightRepo, bookingRepo, seatService);
        BookingResponse resp = service.cancel("FLABCDEF12");

        // FP-08: real flight number + currency, not null / hardcoded "VND".
        assertEquals("VN999", resp.flightNumber());
        assertEquals("USD", resp.currency());
        assertEquals(FlightStatuses.BOOKING_CANCELLED, resp.status());
        // counter is given back atomically.
        verify(flightRepo).incrementAvailableSeats(1L, 2);
    }
}
