package com.flightprovider.web;

import com.flightprovider.service.FlightService;
import com.flightprovider.service.SeatService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FlightDetailController {

    private final FlightService flightService;
    private final SeatService seatService;

    public FlightDetailController(FlightService flightService, SeatService seatService) {
        this.flightService = flightService;
        this.seatService = seatService;
    }

    @GetMapping("/flights/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return flightService.getById(id)
                .map(flight -> {
                    model.addAttribute("flight", flight);
                    model.addAttribute("seatMap", seatService.map(id));
                    return "flight-detail";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("error", "Flight not found");
                    return "redirect:/flights";
                });
    }
}
