package org.example.tears.Repository;

import org.example.tears.Enums.EmployeeRole;
import org.example.tears.Model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.example.tears.Enums.JobTitle;
import org.example.tears.Enums.UserStatus;
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
}