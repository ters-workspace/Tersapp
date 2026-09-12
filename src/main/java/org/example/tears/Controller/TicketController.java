package org.example.tears.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiResponse;
import org.example.tears.DTO.*;
import org.example.tears.Enums.TicketStatus;
import org.example.tears.Model.ChatRoom;
import org.example.tears.Model.User;
import org.example.tears.Service.AuthService;
import org.example.tears.Service.TicketService;
import org.example.tears.Service.WarrantyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tears/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final AuthService authService;
    private final WarrantyService warrantyService;

    @PostMapping("/creat")
    public ApiResponse createTicket(
            @Valid @RequestBody CreateTicketDto dto,
            HttpServletRequest request
    ) {

        return new ApiResponse(
                true,
                "تم إنشاء التذكرة",
                ticketService.createTicket(request, dto)
        );
    }

    @GetMapping("/ticket/{ticketId}/room")
    public ApiResponse getTicketRoom(
            @PathVariable Integer ticketId,
            HttpServletRequest request
    ) {
        User user = authService.getAuthenticatedUser(request);

        ChatRoom room = ticketService.getRoomByTicket(ticketId, user);

        return new ApiResponse(
                true,
                "تم جلب المحادثة",
                new ChatRoomResponse(
                        room.getId(),
                        ticketId,
                        room.getStatus()
                )
        );
    }

    @GetMapping("/my")
    public ApiResponse myTickets(
            HttpServletRequest request
    ){

        return new ApiResponse(
                true,
                "تم جلب التذاكر",
                ticketService.getMyTickets(request)
        );

    }


    @GetMapping("/details/{ticketId}")
    public ApiResponse getTicketDetails(
            @PathVariable Integer ticketId
    ){

        return new ApiResponse(
                true,
                "تم جلب التذكرة",
                ticketService.getTicketDetails(ticketId)
        );

    }

    @PutMapping("/update/{ticketId}/status")
    public ApiResponse updateStatus(
            @PathVariable Integer ticketId,
            @RequestBody @Valid UpdateTicketStatusDto dto,
            HttpServletRequest request
    ){

        ticketService.updateStatus(
                ticketId,
                dto,
                request
        );

        return new ApiResponse(
                true,
                "تم تحديث حالة التذكرة"
        );
    }

    @GetMapping("/search")
    public ApiResponse search(
            @RequestParam String orderNumber
    ){

        return new ApiResponse(
                true,
                "تم جلب النتائج",
                ticketService.searchByOrderNumber(orderNumber)
        );

    }

    @GetMapping("/support/all-ticket")
    public ApiResponse getSupportTickets(
            HttpServletRequest request,
            @RequestParam(required = false) TicketStatus status
    ) {
        return new ApiResponse(
                true,
                "تم جلب التذاكر",
                ticketService.getSupportTickets(request, status)
        );
    }

    @PutMapping("/warranty/{id}/approve")
    public ApiResponse approveWarranty(
            @PathVariable Integer id,
            HttpServletRequest request
    ){

        User user =
                authService.getAuthenticatedUser(request);

        warrantyService.approveWarranty(
                id,
                user.getEmployee()
        );

        return new ApiResponse(
                true,
                "تم قبول طلب الضمان"
        );
    }

    @PutMapping("/warranty/{id}/reject")
    public ApiResponse rejectWarranty(
            @PathVariable Integer id,
            @RequestBody RejectWarrantyDto dto,
            HttpServletRequest request
    ){

        User user =
                authService.getAuthenticatedUser(request);

        warrantyService.rejectWarranty(
                id,
                user.getEmployee(),
                dto.getReason()
        );

        return new ApiResponse(
                true,
                "تم رفض طلب الضمان"
        );
    }

    @GetMapping("/support/count")
    public ApiResponse getSupportTicketCount(
            HttpServletRequest request
    ) {

        return new ApiResponse(
                true,
                "تم جلب عدادات التذاكر",
                ticketService.getSupportTicketCount(request)
        );
    }

    @GetMapping("/support/employees")
    public ApiResponse getSupportEmployees(
            HttpServletRequest request
    ) {

        return new ApiResponse(
                true,
                "تم جلب الموظفين",
                ticketService.getSupportEmployees(request)
        );
    }


    @GetMapping("/support/employees/search")
    public ApiResponse searchSupportEmployees(
            @RequestParam String q,
            HttpServletRequest request
    ) {

        return new ApiResponse(
                true,
                "تم جلب الموظفين",
                ticketService.searchSupportEmployees(
                        request,
                        q
                )
        );
    }

    @GetMapping("/support/tickets/search")
    public ApiResponse search(
            @RequestParam String q,
            HttpServletRequest request
    ){

        return new ApiResponse(
                true,
                "تم جلب النتائج",
                ticketService.searchSupportTickets(
                        request,
                        q
                )
        );
    }


}