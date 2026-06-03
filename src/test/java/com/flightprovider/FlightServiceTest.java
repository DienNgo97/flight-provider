package com.flightprovider;

import com.flightprovider.api.dto.FlightDto;
import com.flightprovider.domain.entity.Flight;
import com.flightprovider.repository.FlightBookingRepository;
import com.flightprovider.repository.FlightRepository;
import com.flightprovider.service.FlightService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlightServiceTest {

    private Flight flight(String from, String to, String time, int seats) {
        Flight f = new Flight();
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
        when(flightRepo.findAll()).thenReturn(List.of(
                flight("HAN", "SGN", "2026-07-01T06:00:00", 180),
                flight("SGN", "HAN", "2026-07-01T06:00:00", 180),
                flight("HAN", "DAD", "2026-07-01T06:00:00", 180)
        ));
        FlightService service = new FlightService(flightRepo, bookingRepo);

        List<FlightDto> result = service.search("HAN", "SGN", null, 1);

        assertEquals(1, result.size());
        assertEquals("SGN", result.get(0).to());
    }
}
