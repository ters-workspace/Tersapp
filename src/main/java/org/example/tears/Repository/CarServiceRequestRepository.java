package org.example.tears.Repository;

import jakarta.persistence.LockModeType;
import org.example.tears.Enums.CustomerRequestStatus;
import org.example.tears.Enums.PricingStatus;
import org.example.tears.Enums.StaffRequestStatus;
import org.example.tears.Enums.WarrantyStatus;
import org.example.tears.Model.CarServiceRequest;
import org.example.tears.Model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface CarServiceRequestRepository extends JpaRepository<CarServiceRequest,Integer> {
    List<CarServiceRequest> findByCustomerIdOrderByIdDesc(Integer customerId);
    List<CarServiceRequest> findByAssignedPricingEmployee(Employee employee);

    List<CarServiceRequest> findByAssignedTechnicianOrAssignedPricingEmployeeAndStaffStatus(
            Employee assignedTechnician,
            Employee assignedPricingEmployee,
            StaffRequestStatus status
    );


    long countByAssignedPricingEmployee_IdAndPricingStatus(
            Integer employeeId,
            PricingStatus pricingStatus
    );

    long countByAssignedPricingEmployee_IdAndPricingStatusIn(
            Integer employeeId,
            List<PricingStatus> statuses
    );

    Optional<CarServiceRequest> findById(Integer id);

    boolean existsByAppointmentDateAndAppointmentTime(
            LocalDate appointmentDate,
            LocalTime appointmentTime
    );

    @Query("""
SELECT r
FROM CarServiceRequest r
JOIN r.car c
WHERE
(:orderNumber IS NULL OR r.orderNumber LIKE CONCAT(:orderNumber, '%'))
AND
(:plateArabic IS NULL OR c.plateNumberArabic LIKE CONCAT(:plateArabic, '%'))
AND
(:plateEnglish IS NULL OR c.plateNumberEnglish LIKE CONCAT(:plateEnglish, '%'))
""")
    List<CarServiceRequest> search(
            @Param("orderNumber") String orderNumber,
            @Param("plateArabic") String plateArabic,
            @Param("plateEnglish") String plateEnglish
    );

    @Query("""
SELECT r
FROM CarServiceRequest r
LEFT JOIN r.currentEmployee e
LEFT JOIN e.user u
WHERE
(:search IS NULL OR
 r.orderNumber LIKE CONCAT('%', :search, '%')
 OR u.fullName LIKE CONCAT('%', :search, '%')
)
""")
    List<CarServiceRequest> searchAD(
            @Param("search") String search
    );

    List<CarServiceRequest> findByAssignedTechnician (Employee emp);
    List<CarServiceRequest> findByAssignedTechnicianIsNullAndAssignedPricingEmployeeIsNullAndAssignedSupportEmployeeIsNull();

    List<CarServiceRequest>
    findByCustomerIdAndCustomerStatusNotInOrderByCreatedAtDesc(
    Integer customerId,
    List<CustomerRequestStatus> statuses
    );


    List<CarServiceRequest> findByCustomerIdAndCustomerStatusInOrderByCreatedAtDesc(
            Integer customerId,
            List<CustomerRequestStatus> statuses
    );
    List<CarServiceRequest> findByAppointmentDate(LocalDate appointmentDate);

    List<CarServiceRequest> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CarServiceRequest r WHERE r.id = :id")
    Optional<CarServiceRequest> findByIdForUpdate(@Param("id") Integer id);

    @Query("""
    SELECT DISTINCT r
    FROM CarServiceRequest r
    LEFT JOIN WarrantyRequest w
        ON w.request = r
    WHERE
        (
            r.assignedTechnician = :employee
            OR r.assignedPricingEmployee = :employee
        )
        AND
        (
            r.staffStatus = :newStatus
            OR (
                w.assignedTechnician = :employee
                AND w.status NOT IN :hiddenWarrantyStatuses
            )
        )
""")
    List<CarServiceRequest> findMyNewRequests(
            @Param("employee") Employee employee,
            @Param("newStatus") StaffRequestStatus newStatus,
            @Param("hiddenWarrantyStatuses") List<WarrantyStatus> hiddenWarrantyStatuses
    );
    @Query("""
    SELECT DISTINCT r
    FROM CarServiceRequest r
    LEFT JOIN WarrantyRequest w
        ON w.request = r
    WHERE
        (
            r.assignedTechnician = :employee
            OR r.assignedPricingEmployee = :employee
        )
        AND r.staffStatus = :deliveredStatus
        AND (
            w.id IS NULL
            OR w.status IN :allowedWarrantyStatuses
        )
""")
    List<CarServiceRequest> findDeliveredRequests(
            @Param("employee") Employee employee,
            @Param("deliveredStatus") StaffRequestStatus deliveredStatus,
            @Param("allowedWarrantyStatuses") List<WarrantyStatus> allowedWarrantyStatuses
    );

    @Query("""
    SELECT COUNT(DISTINCT r.id)
    FROM CarServiceRequest r
    LEFT JOIN WarrantyRequest w
        ON w.request = r
    WHERE 
        (
            r.assignedTechnician.id = :employeeId
            AND r.staffStatus = :newStatus
        )
        OR 
        (
            w.assignedTechnician.id = :employeeId
            AND w.status = :approvedWarrantyStatus
        )
""")
    long countMyNewRequests(
            @Param("employeeId") Integer employeeId,
            @Param("newStatus") StaffRequestStatus newStatus,
            @Param("approvedWarrantyStatus") WarrantyStatus approvedWarrantyStatus
    );


    boolean existsByAppointmentDateAndAppointmentTimeAndCustomerStatusNot(
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            CustomerRequestStatus customerStatus
    );

    List<CarServiceRequest> findByAppointmentDateAndCustomerStatusNot(
            LocalDate appointmentDate,
            CustomerRequestStatus customerStatus
    );

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime start,
            LocalDateTime end
    );

    long countByStaffStatusNotAndLastUpdatedGreaterThanEqualAndLastUpdatedLessThan(
            StaffRequestStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
SELECT COALESCE(SUM(r.finalPrice), 0)
FROM CarServiceRequest r
WHERE r.finalPrice IS NOT NULL
""")
    BigDecimal sumFinalPrice();

    @Query("""
SELECT COUNT(r)
FROM CarServiceRequest r
WHERE
    r.staffStatus = :status
    AND (
        r.assignedTechnician.id = :employeeId
        OR r.assignedPricingEmployee.id = :employeeId
        OR r.assignedSupportEmployee.id = :employeeId
    )
""")
    long countCompletedRequestsByEmployee(
            @Param("employeeId") Integer employeeId,
            @Param("status") StaffRequestStatus status
    );

}
