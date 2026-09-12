package org.example.tears.Repository;

import org.example.tears.Enums.EmployeeRole;
import org.example.tears.Model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.example.tears.Enums.JobTitle;
import org.example.tears.Enums.UserStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee , Integer> {

    List<Employee> findAll();

    List<Employee> findByEmployeeRole(EmployeeRole employeeRole);

    long countByUser_StatusAndJobTitleIn(
            UserStatus status,
            List<JobTitle> jobTitles
    );

    long countByUser_StatusAndJobTitleInAndUser_CreatedAtGreaterThanEqual(
            UserStatus status,
            List<JobTitle> jobTitles,
            java.time.LocalDateTime createdAt
    );

    long countByUser_StatusAndJobTitle(
            UserStatus status,
            JobTitle jobTitle
    );

    long countByUser_Status(UserStatus status);

    @Query("""
SELECT e
FROM Employee e
JOIN e.user u
WHERE
    LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
    OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
    OR u.phoneNumber LIKE CONCAT('%', :search, '%')
    OR LOWER(COALESCE(e.employeeCode, '')) LIKE LOWER(CONCAT('%', :search, '%'))
    OR LOWER(CAST(e.city AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
    OR LOWER(CAST(e.jobTitle AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
    OR LOWER(CAST(u.status AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
""")
    List<Employee> searchEmployees(@Param("search") String search);

}