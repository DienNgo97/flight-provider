package com.flightprovider;

import com.flightprovider.api.dto.SeatHoldResponse;
import com.flightprovider.domain.FlightStatuses;
import com.flightprovider.domain.entity.Flight;
import com.flightprovider.domain.entity.FlightBooking;
import com.flightprovider.domain.entity.FlightSeat;
import com.flightprovider.repository.FlightBookingRepository;
import com.flightprovider.repository.FlightRepository;
import com.flightprovider.repository.FlightSeatRepository;
import com.flightprovider.service.SeatService;
import com.flightprovider.service.SeatTakenException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatServiceTest {

    private Flight flight() {
        Flight f = new Flight();
        f.setId(1L);
        f.setFlightNumber("VN001");
        f.setBasePrice(new BigDecimal("1500000"));
        f.setCurrency("VND");
        f.setTotalSeats(180);
        f.setAvailableSeats(180);
        return f;
    }

    private FlightSeat seat(String code, String status, String holdRef) {
        FlightSeat fs = new FlightSeat();
        fs.setFlightId(1L);
        fs.setSeatCode(code);
        fs.setStatus(status);
        fs.setHoldRef(holdRef);
        return fs;
    }

    /** hold conflict (seat already occupied) -> SeatTakenException (controller maps to 409). */
    @Test
    void holdThrowsSeatTakenWhenSeatAlreadyOccupied() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightSeatRepository seatRepo = mock(FlightSeatRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);

        Flight f = flight();
        when(flightRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(f));
        // 12A is already BOOKED (e.g. by a confirmed seat-selection booking).
        when(seatRepo.findByFlightId(1L)).thenReturn(List.of(seat("12A", FlightStatuses.SEAT_BOOKED, "OTHER")));

        SeatService service = new SeatService(flightRepo, seatRepo, bookingRepo);

        SeatTakenException ex = assertThrows(SeatTakenException.class,
                () -> service.hold(1L, List.of("12A"), "ORDER-1", 20));
        assertTrue(ex.getMessage().contains("12A"));
        // clean message, no raw SQL/constraint leak.
        assertTrue(ex.getMessage().toLowerCase().contains("ghe"));
    }

    /** FP-03: confirm() flips HELD->BOOKED AND creates a FlightBooking with an FL... code. */
    @Test
    void confirmCreatesFlightBookingWithConfirmationCode() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightSeatRepository seatRepo = mock(FlightSeatRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);

        Flight f = flight();
        when(flightRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(f));
        List<FlightSeat> held = new ArrayList<>(List.of(
                seat("12A", FlightStatuses.SEAT_HELD, "ORDER-1"),
                seat("12B", FlightStatuses.SEAT_HELD, "ORDER-1")));
        when(seatRepo.findByFlightIdAndHoldRef(1L, "ORDER-1")).thenReturn(held);
        when(seatRepo.findByFlightId(1L)).thenReturn(held);
        when(bookingRepo.findByHoldRef("ORDER-1")).thenReturn(List.of());
        when(bookingRepo.findByFlightIdAndStatus(eq(1L), any())).thenReturn(List.of());
        when(bookingRepo.sumConfirmedSeats(1L)).thenReturn(0);

        SeatService service = new SeatService(flightRepo, seatRepo, bookingRepo);
        SeatHoldResponse resp = service.confirm(1L, "ORDER-1");

        // A FlightBooking was created...
        ArgumentCaptor<FlightBooking> captor = ArgumentCaptor.forClass(FlightBooking.class);
        verify(bookingRepo).save(captor.capture());
        FlightBooking saved = captor.getValue();
        assertNotNull(saved.getConfirmationCode());
        assertTrue(saved.getConfirmationCode().startsWith("FL"));
        assertEquals("ORDER-1", saved.getHoldRef());
        assertEquals(2, saved.getSeats());
        assertEquals(FlightStatuses.BOOKING_CONFIRMED, saved.getStatus());
        // ...and the response carries that confirmation code (cancellable later).
        assertEquals(saved.getConfirmationCode(), resp.holdRef());
        // seats flipped to BOOKED.
        assertTrue(held.stream().allMatch(s -> FlightStatuses.SEAT_BOOKED.equals(s.getStatus())));
    }

    /**
     * FP-01/FP-04 (single source of truth): a seat held/booked on the seat path
     * is visible to the whole-flight availability number, and a seat already
     * BOOKED via a whole-flight booking's seat rows cannot be re-held. This test
     * proves recomputeAvailable subtracts BOTH seat occupancy and confirmed
     * whole-flight booking seats (no clobber, no double-allocation).
     */
    @Test
    void recomputeCountsSeatRowsAndWholeFlightBookingsWithoutDoubleCounting() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightSeatRepository seatRepo = mock(FlightSeatRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);

        Flight f = flight();
        when(flightRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(f));

        // One standalone seat hold (1 seat) on the seat path...
        List<FlightSeat> rows = new ArrayList<>(List.of(
                seat("10C", FlightStatuses.SEAT_HELD, "ORDER-X")));
        rows.get(0).setExpiresAt(Instant.now().plusSeconds(600));
        when(seatRepo.findByFlightId(1L)).thenReturn(rows);
        when(seatRepo.findByFlightIdAndHoldRef(1L, "ORDER-X")).thenReturn(List.of());

        // ...plus a whole-flight booking of 10 seats (no holdRef) on the book path.
        when(bookingRepo.sumConfirmedSeats(1L)).thenReturn(10);
        when(bookingRepo.findByFlightIdAndStatus(eq(1L), any())).thenReturn(List.of());

        SeatService service = new SeatService(flightRepo, seatRepo, bookingRepo);
        // release() triggers a recompute against the locked flight.
        service.release(1L, "ORDER-X");

        ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepo).save(captor.capture());
        // 180 - 1 (seat row) - 10 (whole-flight booking) = 169.
        assertEquals(169, captor.getValue().getAvailableSeats());
    }

    /**
     * FP-01/FP-04: a confirmed SEAT-SELECTION booking is represented by BOTH its
     * BOOKED seat rows AND a FlightBooking row; recompute must NOT subtract it
     * twice (it carries a holdRef so it's excluded from the whole-flight SUM).
     */
    @Test
    void recomputeDoesNotDoubleCountSeatBackedBookings() {
        FlightRepository flightRepo = mock(FlightRepository.class);
        FlightSeatRepository seatRepo = mock(FlightSeatRepository.class);
        FlightBookingRepository bookingRepo = mock(FlightBookingRepository.class);

        Flight f = flight();
        when(flightRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(f));

        // 2 BOOKED seat rows that belong to a confirmed seat-selection booking.
        List<FlightSeat> rows = new ArrayList<>(List.of(
                seat("1A", FlightStatuses.SEAT_BOOKED, "ORDER-S"),
                seat("1B", FlightStatuses.SEAT_BOOKED, "ORDER-S")));
        when(seatRepo.findByFlightId(1L)).thenReturn(rows);
        when(seatRepo.findByFlightIdAndHoldRef(1L, "ORDER-S")).thenReturn(List.of());

        // The matching FlightBooking (holdRef = ORDER-S, 2 seats) is in the SUM...
        when(bookingRepo.sumConfirmedSeats(1L)).thenReturn(2);
        FlightBooking seatBooking = new FlightBooking();
        seatBooking.setFlightId(1L);
        seatBooking.setHoldRef("ORDER-S");
        seatBooking.setSeats(2);
        seatBooking.setStatus(FlightStatuses.BOOKING_CONFIRMED);
        when(bookingRepo.findByFlightIdAndStatus(1L, FlightStatuses.BOOKING_CONFIRMED))
                .thenReturn(List.of(seatBooking));

        SeatService service = new SeatService(flightRepo, seatRepo, bookingRepo);
        service.release(1L, "ORDER-S");

        ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepo).save(captor.capture());
        // 180 - 2 (seat rows) - 0 (SUM 2 minus seat-backed 2) = 178, NOT 176.
        assertEquals(178, captor.getValue().getAvailableSeats());
    }
}
