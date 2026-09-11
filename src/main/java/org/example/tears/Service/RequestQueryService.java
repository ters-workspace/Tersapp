package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.EmployeeRole;
import org.example.tears.Enums.PricingStatus;
import org.example.tears.Enums.StaffRequestStatus;
import org.example.tears.Enums.WarrantyStatus;
import org.example.tears.Mapper.RequestMapper;
import org.example.tears.Model.CarServiceRequest;
import org.example.tears.Model.Employee;
import org.example.tears.Model.WarrantyRequest;
import org.example.tears.OutDTO.EmployeeRequestDetailsDto;
import org.example.tears.Repository.CarServiceRequestRepository;
import org.example.tears.Repository.RequestImageRepository;
import org.example.tears.Repository.WarrantyRepository;
import org.example.tears.Repository.WarrantyStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestQueryService {

    private final CarServiceRequestRepository requestRepo;
    private final RequestImageRepository imageRepo;
    private final RequestMapper requestMapper;
    private final WarrantyRepository warrantyRepo;
    private final WarrantyStatusHistoryRepository warrantyHistoryRepos;


    public List<RequestSummaryDto> getAllRequests() {
        return requestRepo.findAllByOrderByIdDesc()
                .stream()
                .map(requestMapper::toSummaryDto)
                .toList();
    }

    public long getAllRequestsCount() {
        return requestRepo.count();
    }

    public List<RequestSummaryDto> getUnassigned() {
        return requestRepo.findByAssignedTechnicianIsNullAndAssignedPricingEmployeeIsNullAndAssignedSupportEmployeeIsNull()
                .stream()
                .map(requestMapper::toSummaryDto)
                .toList();
    }

    public List<EmployeeRequestResponseDto> getMyRequests(
            Employee employee,
            StaffRequestStatus status,
            String type
    ) {

        List<CarServiceRequest> requests;

        if (status == null) {

            requests = requestRepo.findMyNewRequests(
                    employee,
                    StaffRequestStatus.NEW,
                    List.of(
                            WarrantyStatus.REQUEST_SENT,
                            WarrantyStatus.PENDING_REVIEW,
                            WarrantyStatus.REJECTED
                    )
            );

        } else if (status == StaffRequestStatus.DELIVERED) {

            requests = requestRepo.findDeliveredRequests(
                    employee,
                    StaffRequestStatus.DELIVERED,
                    List.of(WarrantyStatus.REJECTED)
            );

        } else {

            requests =
                    requestRepo
                            .findByAssignedTechnicianOrAssignedPricingEmployeeAndStaffStatus(
                                    employee,
                                    employee,
                                    status
                            );
        }

        return requests.stream()
                .map(requestMapper::toEmployeeCardDto)
                .filter(dto -> {

                    if ("WARRANTY".equalsIgnoreCase(type)) {
                        return dto.getWarrantyRequest()
                                && dto.getWarrantyStatus() != null
                                && !List.of(
                                WarrantyStatus.PENDING_REVIEW.name(),
                                WarrantyStatus.REQUEST_SENT.name(),
                                WarrantyStatus.REJECTED.name()
                        ).contains(dto.getWarrantyStatus());
                    }

                    if ("NORMAL".equalsIgnoreCase(type)) {

                        return !(
                                Boolean.TRUE.equals(dto.getWarrantyRequest())
                                        && dto.getWarrantyStatus() != null
                                        && !List.of(
                                        WarrantyStatus.PENDING_REVIEW.name(),
                                        WarrantyStatus.REQUEST_SENT.name(),
                                        WarrantyStatus.REJECTED.name()
                                ).contains(dto.getWarrantyStatus())
                        );
                    }

                    return true;
                })
                .sorted(
                        Comparator
                                .comparing(
                                        (EmployeeRequestResponseDto dto) ->
                                                Boolean.TRUE.equals(
                                                        dto.getWarrantyRequest()
                                                ),
                                        Comparator.reverseOrder()
                                )
                                .thenComparing(
                                        EmployeeRequestResponseDto::getCreatedAt,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                )
                .toList();
    }

    public long getMyNewRequestsCount(Employee employee) {

        if (employee.getEmployeeRole() == EmployeeRole.PRICING) {

            return requestRepo.countByAssignedPricingEmployee_IdAndPricingStatus(
                    employee.getId(),
                    PricingStatus.NEW
            );
        }

        return requestRepo.countMyNewRequests(
                employee.getId(),
                StaffRequestStatus.NEW,
                WarrantyStatus.APPROVED
        );
    }

    public EmployeeRequestDetailsDto getRequestDetails(
            Integer requestId,
            Employee employee
    ) {

        CarServiceRequest request =
                requestRepo.findById(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        if (request.getAssignedTechnician() == null ||
                !request.getAssignedTechnician()
                        .getId()
                        .equals(employee.getId())) {

            throw new ApiException("غير مصرح لك");
        }

        EmployeeRequestDetailsDto dto =
                requestMapper.toEmployeeDetailsDto(request);
        WarrantyRequest warranty =
                warrantyRepo.findByRequestId(requestId)
                        .orElse(null);

        if (warranty != null) {

            dto.setWarrantyRequest(true);

            dto.setWarrantyStatus(
                    warranty.getStatus()
            );

            dto.setWarrantyDescription(
                    warranty.getDescription()
            );

            dto.setWarrantyTimeline(
                    getWarrantyTimeline(warranty.getId())
            );

            dto.setWarrantyImages(
                    warranty.getImages()
                            .stream()
                            .map(img -> {

                                WarrantyImageResponseDto imageDto =
                                        new WarrantyImageResponseDto();

                                imageDto.setId(img.getId());
                                imageDto.setImageUrl(
                                        img.getImageUrl()
                                );

                                if (img.getType() != null) {
                                    imageDto.setType(
                                            img.getType().name()
                                    );
                                }

                                return imageDto;
                            })
                            .toList()
            );

        } else {

            dto.setWarrantyRequest(false);
        }


        dto.setImages(
                imageRepo.findByRequest_Id(requestId)
                        .stream()
                        .map(img -> {

                            RequestImageDto imageDto =
                                    new RequestImageDto();

                            imageDto.setId(img.getId());
                            imageDto.setImageUrl(img.getImageUrl());

                            if (img.getUploadedAtStatus() != null) {
                                imageDto.setStatus(
                                        img.getUploadedAtStatus().name()
                                );
                            }

                            imageDto.setUploadedAt(
                                    img.getUploadedAt()
                            );

                            return imageDto;
                        })
                        .toList()
        );
        return dto;
    }

    public List<EmployeeWarrantyStatusHistoryDto> getWarrantyTimeline(
            Integer warrantyId
    ) {

        return warrantyHistoryRepos
                .findByWarrantyRequest_IdOrderByChangedAtAsc(warrantyId)
                .stream()
                .map(history -> {

                    EmployeeWarrantyStatusHistoryDto dto =
                            new EmployeeWarrantyStatusHistoryDto();

                    dto.setStatus(
                            history.getEmployeeStatus()
                    );

                    dto.setChangedAt(
                            history.getChangedAt()
                    );

                    if (history.getChangedBy() != null &&
                            history.getChangedBy().getUser() != null) {

                        dto.setEmployeeName(
                                history.getChangedBy()
                                        .getUser()
                                        .getFullName()
                        );
                    }

                    return dto;
                })
                .toList();
    }



    public List<RequestSummaryDto> search(
            String orderNumber,
            String plateArabic,
            String plateEnglish
    ) {

        return requestRepo.search(
                        orderNumber,
                        plateArabic,
                        plateEnglish
                )
                .stream()
                .map(requestMapper::toSummaryDto)
                .toList();
    }

    public List<RequestSummaryDto> search(String search) {

        return requestRepo.searchAD(search)
                .stream()
                .map(requestMapper::toSummaryDto)
                .toList();
    }

    public List<EmployeeRequestResponseDto> searchMyRequests(
            Employee employee,
            String orderNumber,
            String plateArabic,
            String plateEnglish
    ) {

        List<CarServiceRequest> requests;

        if (employee.getEmployeeRole() == EmployeeRole.PRICING) {

            requests = requestRepo.findByAssignedPricingEmployee(employee);

        } else {

            requests = requestRepo.findByAssignedTechnician(employee);
        }

        return requests.stream()
                .filter(r ->
                        orderNumber == null ||
                                r.getOrderNumber()
                                        .toLowerCase()
                                        .startsWith(orderNumber.toLowerCase())
                )
                .filter(r ->
                        plateArabic == null ||
                                r.getCar()
                                        .getPlateNumberArabic()
                                        .replace(" ", "")
                                        .startsWith(
                                                plateArabic.replace(" ", "")
                                        )
                )
                .filter(r ->
                        plateEnglish == null ||
                                r.getCar()
                                        .getPlateNumberEnglish()
                                        .toLowerCase()
                                        .startsWith(plateEnglish.toLowerCase())
                )
                .map(requestMapper::toEmployeeCardDto)
                .toList();
    }



}