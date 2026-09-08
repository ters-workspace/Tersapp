package org.example.tears.Service;

import org.example.tears.Enums.*;
import org.example.tears.Mapper.PricingRequestMapper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Model.*;
import org.example.tears.Repository.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;

import java.util.Base64;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RequestPricingService {

    private final CarServiceRequestRepository requestRepo;
    private final RequestPartRepository partRepo;
    private final RequestStatusHistoryRepository historyRepo;
    private final NotificationService notificationService;
    private final RequestReportRepository reportRepo;
    private final RequestNoteRepository noteRepo;
    private final RequestApprovalRepository approvalRepo;
    private final SocketService socketService;
    private final CarServiceRequestService carServiceRequestService;
    private final PricingRequestMapper pricingRequestMapper;

    @Transactional
    public void startPricing(Integer requestId, Employee employee){

        CarServiceRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ApiException("الطلب غير موجود"));

        if(request.getAssignedPricingEmployee() == null ||
                !request.getAssignedPricingEmployee().getId().equals(employee.getId())){
            throw new ApiException("الطلب غير مسند لك");
        }

        request.setPricingStatus(PricingStatus.PRICING);
        request.setPricingAt(LocalDateTime.now());

        requestRepo.save(request);

        socketService.send(
                "/topic/pricing-request-details/" +
                        request.getId(),

                pricingRequestMapper.toPricingDetailsDto(request)
        );
    }

    @Transactional
    public void pricingRequest(
            Integer requestId,
            PricingRequestDto dto,
            Employee pricingEmployee
    ) {

        CarServiceRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ApiException("الطلب غير موجود"));

        if (request.getAssignedPricingEmployee() == null ||
                !request.getAssignedPricingEmployee().getId().equals(pricingEmployee.getId())) {

            throw new ApiException("الطلب غير مسند لك");
        }

        if (request.getPricingStatus() != PricingStatus.PRICING) {
            throw new ApiException("يجب بدء التسعير أولاً");
        }

        int totalPartsPrice = 0;
        int totalLabor = 0;

        for (PricingPartDto item : dto.getParts()) {

            RequestPart part = partRepo.findById(item.getPartId())
                    .orElseThrow(() -> new ApiException("القطعة غير موجودة"));

            if (!part.getRequest().getId().equals(requestId)) {
                throw new ApiException("القطعة لا تتبع هذا الطلب");
            }

            part.setFinalPrice(item.getFinalPrice());
            part.setPriced(true);

            partRepo.save(part);

            totalPartsPrice += item.getFinalPrice() * part.getQuantity();
            totalLabor += part.getLaborCost();
        }

        request.setFinalPrice(totalPartsPrice + totalLabor);

        request.setLastUpdated(LocalDateTime.now());

        requestRepo.save(request);
        socketService.send(
                "/topic/pricing-request-details/" +
                        request.getId(),

                pricingRequestMapper.toPricingDetailsDto(request)
        );
    }

    @Transactional
    public void finishPricing(
            Integer requestId,
            Employee pricingEmployee
    ) {

        CarServiceRequest request =
                requestRepo.findById(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        if (request.getAssignedPricingEmployee() == null ||
                !request.getAssignedPricingEmployee()
                        .getId()
                        .equals(pricingEmployee.getId())) {

            throw new ApiException("الطلب غير مسند لك");
        }

        if (request.getPricingStatus() != PricingStatus.PRICING) {
            throw new ApiException("الطلب ليس في مرحلة التسعير");
        }

        List<RequestPart> parts =
                partRepo.findByRequestId(requestId);

        if (parts.isEmpty()) {
            throw new ApiException("لا توجد قطع في الطلب");
        }

        boolean allPriced =
                parts.stream()
                        .allMatch(part ->
                                Boolean.TRUE.equals(part.getPriced())
                        );

        if (!allPriced) {
            throw new ApiException(
                    "يجب تسعير جميع القطع أولاً"
            );
        }

        /*
         * ==========================================
         * حساب التقرير
         * ==========================================
         */

        double totalPartsPrice = 0;
        double totalLabor = 0;

        for (RequestPart part : parts) {

            int price =
                    part.getFinalPrice() == null
                            ? 0
                            : part.getFinalPrice();

            int quantity =
                    part.getQuantity() == null
                            ? 0
                            : part.getQuantity();

            int labor =
                    part.getLaborCost() == null
                            ? 0
                            : part.getLaborCost();

            totalPartsPrice +=
                    (double) price * quantity;

            totalLabor += labor;
        }

        double subtotal =
                totalPartsPrice + totalLabor;

        /*
         * ==========================================
         * الخصم
         * ==========================================
         */

        double discount = 0;

        Coupon coupon = request.getCoupon();

        if (coupon != null) {

            if (coupon.getDiscountPercentage() != null) {

                discount =
                        subtotal *
                                coupon.getDiscountPercentage()
                                / 100.0;

                if (coupon.getMaxDiscountAmount() != null) {

                    discount =
                            Math.min(
                                    discount,
                                    coupon.getMaxDiscountAmount()
                            );
                }
            }

            if (coupon.getFixedDiscount() != null) {

                discount +=
                        coupon.getFixedDiscount();
            }

            discount =
                    Math.min(discount, subtotal);
        }

        /*
         * ==========================================
         * بعد الخصم
         * ==========================================
         */

        double afterDiscount =
                Math.max(
                        subtotal - discount,
                        0
                );

        /*
         * ==========================================
         * VAT
         * ==========================================
         */

        double vatAmount =
                afterDiscount * 0.15;

        /*
         * ==========================================
         * النهائي
         * ==========================================
         */

        double grandTotal =
                afterDiscount + vatAmount;

        /*
         * حفظ الحسابات في الطلب
         */

        request.setOriginalPrice(
                round(subtotal)
        );

        request.setDiscount(
                round(discount)
        );

        request.setVatAmount(
                round(vatAmount)
        );

        request.setFinalPrice(
                (int) Math.round(grandTotal)
        );

        request.setCouponValid(
                coupon != null
        );

        request.setPricingStatus(
                PricingStatus.PRICED
        );

        request.setPricingCompletedAt(
                LocalDateTime.now()
        );

        saveHistory(
                request,
                pricingEmployee.getId()
        );

        /*
         * ==========================================
         * إغلاق التقرير السابق
         * ==========================================
         */

        RequestReport oldReport =
                reportRepo.findByRequest_IdAndLatestTrue(requestId)
                        .orElse(null);

        int version = 1;

        if (oldReport != null) {

            oldReport.setLatest(false);

            reportRepo.save(oldReport);

            version =
                    oldReport.getVersion() + 1;
        }

        /*
         * ==========================================
         * إنشاء التقرير
         * ==========================================
         */

        RequestReport report =
                new RequestReport();

        report.setRequest(request);
        report.setCreatedBy(pricingEmployee);
        report.setCreatedAt(LocalDateTime.now());
        report.setVersion(version);
        report.setLatest(true);
        report.setSent(false);

        report.setVersionType(
                ReportVersionType.PRICING
        );

        reportRepo.save(report);

        /*
         * بعد الحفظ نولد رقم التقرير
         */

        report.setReportNumber(
                "PR-" +
                        String.format(
                                "%06d",
                                report.getId()
                        )
        );

        reportRepo.save(report);

        /*
         * ==========================================
         * Snapshot للقطع
         * ==========================================
         */

        clonePartsToReport(
                request,
                report
        );

        /*
         * ==========================================
         * تحديث حالة الطلب
         * ==========================================
         */

        request.setCurrentEmployee(
                request.getAssignedTechnician()
        );

        request.setStaffStatus(
                StaffRequestStatus.REPORT_WRITING
        );

        request.setReportWrittenAt(
                LocalDateTime.now()
        );

        request.setLastUpdated(
                LocalDateTime.now()
        );

        requestRepo.save(request);

        socketService.send(
                "/topic/current-orders/" +
                        request.getCustomer()
                                .getUser()
                                .getId(),
                carServiceRequestService.toCurrentDto(request)
        );
        socketService.send(
                "/topic/request/" +
                        request.getId(),
                carServiceRequestService.toDetailsDto(request)
        );
        socketService.send(
                "/topic/pricing-request-details/" +
                        request.getId(),

                pricingRequestMapper.toPricingDetailsDto(request)
        );

        /*
         * ==========================================
         * Approval
         * ==========================================
         */

        RequestApproval approval =
                approvalRepo.findByRequest_Id(requestId)
                        .orElse(null);

        if (approval == null) {

            approval =
                    new RequestApproval();

            approval.setRequest(request);
        }

        approval.setApproved(null);
        approval.setDecisionAt(null);
        approval.setCustomerNote(null);

        approvalRepo.save(approval);

        /*
         * ==========================================
         * Notification
         * ==========================================
         */

        notificationService.send(
                request.getCustomer().getUser(),

                NotificationType.QUOTATION_CREATED,
                NotificationCategory.QUOTATION,

                "تم تجهيز تقرير التسعير",

                "تم تجهيز تقرير التسعير للطلب #"
                        + request.getOrderNumber()
                        + "، يرجى مراجعته.",

                NotificationActionType.OPEN_ENTITY,
                NotificationEntityType.REQUEST,
                request.getId().toString(),
                NotificationSection.REQUESTS
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public ResponseEntity<byte[]> downloadPricingReport(
            Integer requestId,
            Employee employee
    ) throws Exception {

        RequestReport report =
                getAccessibleReport(requestId, employee);

        return generatePdf(report);
    }


    public ResponseEntity<byte[]> generatePdf(
            RequestReport report
    ) throws Exception {

        // =========================================
        // REQUEST
        // =========================================

        CarServiceRequest request =
                report.getRequest();


        // =========================================
        // PARTS
        // =========================================

        List<RequestPart> parts =
                partRepo.findByReport_Id(
                        report.getId()
                );


        // =========================================
        // TECHNICIAN NOTES
        // =========================================

        List<RequestNote> notes =
                noteRepo.findByRequestOrderByCreatedAtDesc(
                        request
                );


        // =========================================
        // BUILD PARTS ROWS
        // =========================================

        StringBuilder rows =
                new StringBuilder();


        double totalPartsPrice = 0;

        double totalLabor = 0;

        int index = 1;


        for (RequestPart part : parts) {

            // -------------------------
            // PART PRICE
            // -------------------------

            double partPrice =
                    part.getFinalPrice() == null
                            ? 0
                            : part.getFinalPrice();


            // -------------------------
            // QUANTITY
            // -------------------------

            int quantity =
                    part.getQuantity() == null
                            ? 0
                            : part.getQuantity();


            // -------------------------
            // LABOR
            // -------------------------

            double labor =
                    part.getLaborCost() == null
                            ? 0
                            : part.getLaborCost();


            // -------------------------
            // PARTS COST
            // -------------------------

            double partsCost =
                    partPrice * quantity;


            // -------------------------
            // ROW TOTAL
            // -------------------------

            double total =
                    partsCost + labor;


            // -------------------------
            // ACCUMULATE TOTALS
            // -------------------------

            totalPartsPrice += partsCost;

            totalLabor += labor;


            // -------------------------
            // ADD ROW
            // -------------------------

            rows.append("""
    <tr>

        <!-- LEFT : TOTAL -->
        <td class="money-value">
            <span dir="ltr">%s SAR</span>
        </td>

        <!-- LABOR -->
        <td class="money-value">
            <span dir="ltr">%s SAR</span>
        </td>

        <!-- PART PRICE -->
        <td class="money-value">
            <span dir="ltr">%s SAR</span>
        </td>

        <!-- QUANTITY -->
        <td class="part-number">
            <span dir="ltr">%d</span>
        </td>

        <!-- TYPE -->
        <td class="part-type">
            %s
        </td>

        <!-- PART NAME -->
        <td class="part-name">
            %s
        </td>

        <!-- RIGHT : INDEX -->
        <td class="part-number">
            <span dir="ltr">%d</span>
        </td>

    </tr>
    """.formatted(
                    formatMoney(total),
                    formatMoney(labor),
                    formatMoney(partPrice),
                    quantity,
                    escapeHtml(
                            part.getType() == null
                                    ? "-"
                                    : part.getType()
                    ),
                    escapeHtml(
                            part.getName() == null
                                    ? "-"
                                    : part.getName()
                    ),
                    index++
            ));
        }


        // =========================================
        // EMPTY PARTS
        // =========================================

        if (parts.isEmpty()) {

            rows.append("""
        <tr class="empty-row">

            <td colspan="7">
                لا توجد قطع مسجلة
            </td>

        </tr>
        """);
        }


        // =========================================
        // TOTALS
        // =========================================

        double subtotal =
                totalPartsPrice + totalLabor;


        // =========================================
        // DISCOUNT
        // =========================================

        double discount =
                request.getDiscount() == null
                        ? 0
                        : request.getDiscount();


        discount =
                Math.min(
                        discount,
                        subtotal
                );


        // =========================================
        // AFTER DISCOUNT
        // =========================================

        double afterDiscount =
                Math.max(
                        subtotal - discount,
                        0
                );


        // =========================================
        // VAT
        // =========================================

        double vat =
                request.getVatAmount() == null
                        ? afterDiscount * 0.15
                        : request.getVatAmount();


        // =========================================
        // GRAND TOTAL
        // =========================================

        double grandTotal =
                afterDiscount + vat;


        // =========================================
        // TECHNICIAN NOTES SECTION
        // =========================================

        String technicianNotesSection =
                "";


        if (!notes.isEmpty()
                && notes.get(0).getNote() != null
                && !notes.get(0)
                .getNote()
                .isBlank()) {

            technicianNotesSection =
                    """
                    <div class="section">
    
                        <table class="section-title-table">
    
                            <tr>
    
                                <td class="section-title-ar-cell">
                                    ملاحظات الفني
                                </td>
    
                                <td class="section-title-en-cell">
                                    Technician Notes
                                </td>
    
                            </tr>
    
                        </table>
    
    
                        <div class="notes-box">
                            %s
                        </div>
    
                    </div>
                    """.formatted(

                            escapeHtml(
                                    notes.get(0)
                                            .getNote()
                            )
                    );
        }


        // =========================================
        // LOAD LOGO
        // =========================================

        ClassPathResource logoResource =
                new ClassPathResource(
                        "reports/images/ters-logo.png"
                );


        String logoBase64 =
                Base64.getEncoder()
                        .encodeToString(

                                logoResource
                                        .getInputStream()
                                        .readAllBytes()
                        );


        // =========================================
        // LOAD HTML TEMPLATE
        // =========================================

        ClassPathResource resource =
                new ClassPathResource(
                        "templates/pricing-report.html"
                );


        String html =
                new String(

                        resource
                                .getInputStream()
                                .readAllBytes(),

                        StandardCharsets.UTF_8
                );


        // =========================================
        // REPLACE LOGO
        // =========================================

        html = html.replace(
                "${logo}",
                logoBase64
        );


        // =========================================
        // CUSTOMER
        // =========================================

        html = html.replace(
                "${customerName}",

                escapeHtml(
                        request.getCustomer()
                                .getUser()
                                .getFullName()
                )
        );


        // =========================================
        // ORDER NUMBER
        // =========================================

        html = html.replace(
                "${orderNumber}",

                escapeHtml(
                        request.getOrderNumber() == null
                                ? "-"
                                : request.getOrderNumber()
                )
        );


        // =========================================
        // REPORT NUMBER
        // =========================================

        html = html.replace(
                "${reportNumber}",

                escapeHtml(
                        report.getReportNumber() == null
                                ? "-"
                                : report.getReportNumber()
                )
        );


        // =========================================
        // CAR MODEL
        // =========================================

        html = html.replace(
                "${carModel}",

                escapeHtml(

                        request.getCar() == null
                                || request.getCar()
                                .getModel().getNameAr() == null

                                ? "-"

                                : request.getCar()
                                .getModel()
                                .getNameAr()
                )
        );


        // =========================================
        // SERVICE OPTION
        // =========================================

        html = html.replace(
                "${serviceOption}",

                escapeHtml(

                        request.getServiceOption() == null
                                ? "-"
                                : request.getServiceOption()
                                .getDisplayName()
                )
        );


        // =========================================
        // PHONE
        // =========================================

        html = html.replace(
                "${phone}",

                escapeHtml(

                        request.getCustomer() == null
                                || request.getCustomer()
                                .getUser() == null

                                ? "-"

                                : request
                                .getCustomer()
                                .getUser()
                                .getPhoneNumber()
                )
        );


        // =========================================
        // PROBLEM
        // =========================================

        html = html.replace(
                "${problem}",

                escapeHtml(

                        request.getProblemDescription() == null
                                || request
                                .getProblemDescription()
                                .isBlank()

                                ? "-"

                                : request
                                .getProblemDescription()
                )
        );


        // =========================================
        // PARTS ROWS
        // =========================================

        html = html.replace(
                "${rows}",
                rows.toString()
        );


        // =========================================
        // TOTALS
        // =========================================

        html = html.replace(
                "${partsTotal}",
                formatMoney(totalPartsPrice)
        );


        html = html.replace(
                "${laborTotal}",
                formatMoney(totalLabor)
        );


        html = html.replace(
                "${subtotal}",
                formatMoney(subtotal)
        );


        html = html.replace(
                "${discount}",
                formatMoney(discount)
        );


        html = html.replace(
                "${afterDiscount}",
                formatMoney(afterDiscount)
        );


        html = html.replace(
                "${vat}",
                formatMoney(vat)
        );


        html = html.replace(
                "${grandTotal}",
                formatMoney(grandTotal)
        );


        // =========================================
        // TECHNICIAN NOTES
        // =========================================

        html = html.replace(
                "${technicianNotesSection}",
                technicianNotesSection
        );


        // =========================================
        // PDF OUTPUT
        // =========================================

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();


        PdfRendererBuilder builder =
                new PdfRendererBuilder();


        // =========================================
        // RTL SUPPORT
        // =========================================

        builder.useUnicodeBidiSplitter(
                new ICUBidiSplitter
                        .ICUBidiSplitterFactory()
        );


        builder.useUnicodeBidiReorderer(
                new ICUBidiReorderer()
        );


        builder.defaultTextDirection(
                BaseRendererBuilder
                        .TextDirection
                        .RTL
        );


        // =========================================
        // CAIRO FONT
        // =========================================

        ClassPathResource cairoRegular =
                new ClassPathResource(
                        "fonts/Cairo-Regular.ttf"
                );

        ClassPathResource cairoBold =
                new ClassPathResource(
                        "fonts/Cairo-Bold.ttf"
                );

        builder.useFont(
                () -> {
                    try {
                        return cairoRegular.getInputStream();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                "Cairo",
                400,
                PdfRendererBuilder.FontStyle.NORMAL,
                true
        );

        builder.useFont(
                () -> {
                    try {
                        return cairoBold.getInputStream();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                "Cairo",
                700,
                PdfRendererBuilder.FontStyle.NORMAL,
                true
        );


        // =========================================
        // HTML
        // =========================================

        builder.withHtmlContent(

                html,

                new ClassPathResource("")
                        .getURL()
                        .toExternalForm()
        );


        // =========================================
        // OUTPUT
        // =========================================

        builder.toStream(
                output
        );


        builder.run();


        // =========================================
        // RESPONSE
        // =========================================

        return ResponseEntity
                .ok()

                .header(

                        HttpHeaders
                                .CONTENT_DISPOSITION,

                        "attachment;filename=pricing-report-"+report.getReportNumber()+".pdf"
                )

                .contentType(
                        MediaType.APPLICATION_PDF
                )

                .body(
                        output.toByteArray()
                );
    }



    private String formatMoney(double value) {
        return String.format(
                Locale.US,
                "%.2f",
                value
        );
    }
    private String escapeHtml(String value) {

        if (value == null) {
            return "-";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


    public ReportPreviewDto getEmpReport(
            Integer requestId,
            Employee employee
    ) {

        CarServiceRequest request =
                requestRepo.findById(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        // ==========================================
        // تحديد التقرير حسب الموظف
        // ==========================================

        RequestReport report =
                getAccessibleReport(
                        requestId,
                        employee
                );

        // ==========================================
        // جلب Snapshot القطع الخاصة بهذا التقرير
        // ==========================================

        List<RequestPart> parts =
                partRepo.findByReport_Id(
                        report.getId()
                );
        ReportPreviewDto dto =
                new ReportPreviewDto();

        dto.setRequestId(
                request.getId()
        );

        dto.setOrderNumber(
                request.getOrderNumber()
        );

        dto.setCustomerName(
                request.getCustomer()
                        .getUser()
                        .getFullName()
        );

        dto.setCarModel(
                request.getCar()
                        .getModel()
                        .getName()
        );

        dto.setProblemDescription(
                request.getProblemDescription()
        );

        dto.setServiceType(
                request.getServiceOption()
                        .name()
        );

        approvalRepo.findByRequest_Id(requestId)
                .ifPresent(
                        a -> dto.setCustomerApproved(
                                a.getApproved()
                        )
                );

        List<CustomerReportPartDto> list =
                new ArrayList<>();

        int totalPartsPrice = 0;
        int totalLabor = 0;

        for (RequestPart part : parts) {

            CustomerReportPartDto p =
                    new CustomerReportPartDto();

            p.setPartId(
                    part.getId()
            );

            p.setName(
                    part.getName()
            );

            p.setQuantity(
                    part.getQuantity()
            );

            p.setFinalPrice(
                    part.getFinalPrice()
            );

            p.setLaborCost(
                    part.getLaborCost()
            );

            p.setType(
                    part.getType()
            );

            int partPrice =
                    part.getFinalPrice() == null
                            ? 0
                            : part.getFinalPrice();

            int quantity =
                    part.getQuantity() == null
                            ? 0
                            : part.getQuantity();

            int labor =
                    part.getLaborCost() == null
                            ? 0
                            : part.getLaborCost();

            int partsCost =
                    partPrice * quantity;

            int total =
                    partsCost + labor;

            p.setTotal(
                    (double) total
            );

            totalPartsPrice +=
                    partsCost;

            totalLabor +=
                    labor;

            list.add(p);
        }

        // ==========================================
        // Financial Calculation
        // ==========================================

        double subtotal =
                totalPartsPrice + totalLabor;

        double discount =
                request.getDiscount() == null
                        ? 0
                        : request.getDiscount();

        discount =
                Math.min(
                        discount,
                        subtotal
                );

        double afterDiscount =
                Math.max(
                        subtotal - discount,
                        0
                );

        double vat =
                afterDiscount * 0.15;

        double grandTotal =
                afterDiscount + vat;

        // ==========================================
        // DTO
        // ==========================================

        dto.setParts(list);

        dto.setTotalPartsPrice(
                totalPartsPrice
        );

        dto.setTotalLabor(
                totalLabor
        );

        dto.setDiscount(
                discount
        );

        dto.setVatAmount(
                vat
        );

        dto.setSubtotal(
                subtotal
        );

        dto.setAfterDiscount(
                afterDiscount
        );

        dto.setGrandTotal(
                grandTotal
        );

        return dto;
    }

    public void clonePartsToReport(
            CarServiceRequest request,
            RequestReport report
    ) {

        List<RequestPart> currentParts =
                partRepo.findByRequestId(request.getId());

        for (RequestPart oldPart : currentParts) {

            RequestPart copy = new RequestPart();

            copy.setRequest(request);
            copy.setReport(report);

            copy.setName(
                    oldPart.getName()
            );

            copy.setType(
                    oldPart.getType()
            );

            copy.setQuantity(
                    oldPart.getQuantity()
            );

            copy.setNotes(
                    oldPart.getNotes()
            );

            copy.setFinalPrice(
                    oldPart.getFinalPrice()
            );

            copy.setLaborCost(
                    oldPart.getLaborCost()
            );

            copy.setPriced(
                    oldPart.getPriced()
            );

            partRepo.save(copy);
        }
    }


    private RequestReport getAccessibleReport(
            Integer requestId,
            Employee employee
    ) {

        if (employee.getEmployeeRole() == EmployeeRole.PRICING) {

            return reportRepo
                    .findTopByRequest_IdAndCreatedBy_IdAndVersionTypeOrderByVersionDesc(
                            requestId,
                            employee.getId(),
                            ReportVersionType.PRICING
                    )
                    .orElseThrow(() ->
                            new ApiException(
                                    "لا يوجد تقرير تسعير لهذا الموظف"
                            )
                    );
        }

        return reportRepo
                .findByRequest_IdAndLatestTrue(requestId)
                .orElseThrow(() ->
                        new ApiException(
                                "لا يوجد تقرير"
                        )
                );
    }


    private void saveHistory(
            CarServiceRequest req,
            Integer empId
    ) {

        RequestStatusHistory h = new RequestStatusHistory();

        h.setRequest(req);
        h.setStaffStatus(req.getStaffStatus());
        h.setCustomerStatus(req.getCustomerStatus());
        h.setPricingStatus(req.getPricingStatus());

        h.setChangedBy(empId);
        h.setChangedAt(LocalDateTime.now());

        historyRepo.save(h);
    }

}