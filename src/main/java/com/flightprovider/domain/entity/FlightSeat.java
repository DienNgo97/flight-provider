package com.flightprovider.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Ghe da bi GIU (HELD) hoac da DAT (BOOKED) cua 1 chuyen.
 * Ghe trong = khong co dong nao trong bang nay (so do day du suy ra tu SeatLayout).
 */
@Entity
@Table(name = "flight_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_flight_seat", columnNames = {"flight_id", "seat_code"}))
public class FlightSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "seat_code", nullable = false, length = 4)
    private String seatCode;

    /** HELD = dang giu cho (co expiresAt); BOOKED = da thanh toan. */
    @Column(nullable = false, length = 10)
    private String status;

    /** Ma tham chieu giu cho (= ma don ben booking-platform). */
    @Column(name = "hold_ref", length = 40)
    private String holdRef;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }
    public String getSeatCode() { return seatCode; }
    public void setSeatCode(String seatCode) { this.seatCode = seatCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHoldRef() { return holdRef; }
    public void setHoldRef(String holdRef) { this.holdRef = holdRef; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
