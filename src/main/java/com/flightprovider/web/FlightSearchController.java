package com.flightprovider.web;

import com.flightprovider.api.dto.FlightDto;
import com.flightprovider.service.FlightService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class FlightSearchController {

    private final FlightService flightService;

    public FlightSearchController(FlightService flightService) {
        this.flightService = flightService;
    }

    @GetMapping("/flights")
    public String search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        List<FlightDto> flights = flightService.search(from, to, date, 1);
        model.addAttribute("flights", flights);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("date", date);
        return "flights";
    }
}
