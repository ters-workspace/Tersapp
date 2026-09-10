package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.SlotDto;
import org.example.tears.Enums.AppointmentSlotStatus;
import org.example.tears.Enums.CustomerRequestStatus;
import org.example.tears.Enums.PaymentStatus;
import org.example.tears.Model.CarServiceRequest;
import org.example.tears.Repository.CarServiceRequestRepository;
import org.example.tears.Repository.PaymentIntentRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final CarServiceRequestRepository requestRepository;
    private final PaymentIntentRepository paymentIntentRepository;

    private static final List<LocalTime> AVAILABLE_TIMES =
            List.of(
                    LocalTime.of(8, 0),
                    LocalTime.of(9, 0),
                    LocalTime.of(10, 0),
                    LocalTime.of(11, 0),
                    LocalTime.of(12, 0),
                    LocalTime.of(16, 0),
                    LocalTime.of(17, 0),
                    LocalTime.of(18, 0),
                    LocalTime.of(19, 0)
            );

    public void validateAppointment(
            LocalDate date,
            LocalTime time
    ) {

        // التاريخ السابق
        if (date.isBefore(LocalDate.now())) {
            throw new ApiException(
                    "لا يمكن الحجز بتاريخ سابق"
            );
        }

        // الجمعة إجازة
        if (date.getDayOfWeek() == DayOfWeek.FRIDAY) {
            throw new ApiException(
                    "لا يمكن الحجز يوم الجمعة"
            );
        }

        // الوقت خارج أوقات الدوام
        if (!AVAILABLE_TIMES.contains(time)) {
            throw new ApiException(
                    "وقت غير متاح"
            );
        }

        // الموعد محجوز فعليًا
        boolean requestExists =
                requestRepository
                        .existsByAppointmentDateAndAppointmentTimeAndCustomerStatusNot(
                                date,
                                time,
                                CustomerRequestStatus.CANCELED
                        );

        // موعد عليه عملية دفع معلقة
        boolean paymentIntentExists =
                paymentIntentRepository
                        .existsByAppointmentDateAndAppointmentTimeAndPaymentStatusInAndExpiresAtAfter(
                                date,
                                time,
                                List.of(
                                        PaymentStatus.PENDING,
                                        PaymentStatus.INITIATED
                                ),
                                LocalDateTime.now()
                        );

        if (requestExists || paymentIntentExists) {
            throw new ApiException(
                    "هذا الموعد محجوز"
            );
        }
    }

    public Map<String, Object> getAvailability(LocalDate date) {

        List<CarServiceRequest> requests =
                requestRepository.findByAppointmentDateAndCustomerStatusNot(
                        date,
                        CustomerRequestStatus.CANCELED
                );

        List<SlotDto> slots = new ArrayList<>();

        for (LocalTime time : AVAILABLE_TIMES) {

            SlotDto slot = new SlotDto();
            slot.setTime(time);

            boolean requestExists = requests.stream()
                    .anyMatch(r -> time.equals(r.getAppointmentTime()));

            boolean pendingPaymentExists =
                    paymentIntentRepository
                            .existsByAppointmentDateAndAppointmentTimeAndPaymentStatusInAndExpiresAtAfter(
                                    date,
                                    time,
                                    List.of(
                                            PaymentStatus.PENDING,
                                            PaymentStatus.INITIATED
                                    ),
                                    LocalDateTime.now()
                            );

            if (requestExists) {
                slot.setStatus(AppointmentSlotStatus.BOOKED);
            } else if (pendingPaymentExists) {
                slot.setStatus(AppointmentSlotStatus.PENDING);
            } else {
                slot.setStatus(AppointmentSlotStatus.AVAILABLE);
            }

            slots.add(slot);
        }

        long available = slots.stream()
                .filter(s -> s.getStatus() == AppointmentSlotStatus.AVAILABLE)
                .count();

        long pending = slots.stream()
                .filter(s -> s.getStatus() == AppointmentSlotStatus.PENDING)
                .count();

        long booked = slots.stream()
                .filter(s -> s.getStatus() == AppointmentSlotStatus.BOOKED)
                .count();

        boolean fullBooked = available == 0;

        return Map.of(
                "date", date,
                "fullBooked", fullBooked,
                "summary", Map.of(
                        "available", available,
                        "pending", pending,
                        "booked", booked
                ),
                "slots", slots
        );
    }

//    public List<Map<String, Object>> getAllAvailability() {
//
//        List<Map<String, Object>> result = new ArrayList<>();
//        LocalDate start = LocalDate.now();
//
//        for (int i = 0; i < 15; i++) {
//            result.add(getAvailability(start.plusDays(i)));
//        }
//
//        return result;
//    }

    public List<Map<String, Object>> getAllAvailability() {

        List<Map<String, Object>> result = new ArrayList<>();

        LocalDate start = LocalDate.now();

        for (int i = 0; i < 10; i++) {

            LocalDate date = start.plusDays(i);

            List<LocalTime> bookedTimes =
                    requestRepository
                            .findByAppointmentDateAndCustomerStatusNot(
                                    date,
                                    CustomerRequestStatus.CANCELED
                            )
                            .stream()
                            .map(CarServiceRequest::getAppointmentTime)
                            .filter(Objects::nonNull)
                            .distinct()
                            .sorted()
                            .toList();

            boolean fullBooked =
                    bookedTimes.size() == AVAILABLE_TIMES.size();

            result.add(
                    Map.of(
                            "date", date,
                            "fullBooked", fullBooked,
                            "bookedTimes", bookedTimes
                    )
            );
        }

        return result;
    }

}