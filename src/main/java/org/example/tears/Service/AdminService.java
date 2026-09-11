package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.Api.ApiResponse;
import org.example.tears.Config.PasswordGenerator;
import org.example.tears.Config.TempEmailGenerator;
import org.example.tears.DTO.EmployeeListDto;
import org.example.tears.Enums.UserRole;
import org.example.tears.Enums.UserStatus;
import org.example.tears.InpDTO.AdminCreateEmployeeDTO;
import org.example.tears.Mapper.RequestMapper;
import org.example.tears.Model.Employee;
import org.example.tears.Model.User;
import org.example.tears.OutDTO.EmployeeLoginInfo;
import org.example.tears.Repository.EmployeeRepository;
import org.example.tears.Repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;

import java.time.LocalDateTime;
import java.util.List;
@Service
@RequiredArgsConstructor
public class AdminService {
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passGen;
    private final TempEmailGenerator emailGen;
    private final RequestMapper requestMapper;
    private final UserRepository userRepo;


    // ================= Employee (Admin registers only) =================
    public EmployeeLoginInfo createEmployee(
            AdminCreateEmployeeDTO dto
    ) {

        if (userRepo.existsByPhoneNumber(
                dto.getPhoneNumber()
        )) {

            throw new ApiException(
                    "Phone already used"
            );
        }

        String email =
                emailGen.generate(
                        dto.getFullName()
                );

        String rawPass =
                passGen.generate();

        User user =
                new User();

        user.setFullName(
                dto.getFullName()
        );

        user.setPhoneNumber(
                dto.getPhoneNumber()
        );

        user.setEmail(
                email
        );

        user.setPassword(
                passwordEncoder.encode(
                        rawPass
                )
        );

        user.setRole(
                UserRole.EMPLOYEE
        );

        user.setStatus(
                UserStatus.ACTIVE
        );

        user.setCreatedAt(LocalDateTime.now());

        User savedUser =
                userRepo.save(
                        user
                );

        Employee employee =
                new Employee();

        employee.setUser(
                savedUser
        );

        employee.setCity(dto.getCity());

        employee.setJobTitle(
                dto.getJobTitle()
        );


        employee.setEmployeeRole(
                dto.getEmployeeRole()
        );

        employee.setMustChangePassword(false);

        employeeRepository.save(
                employee
        );
        employee.setEmployeeCode(
                String.format("EM-%06d", employee.getId())
        );
        employeeRepository.save(
                employee
        );

        return new EmployeeLoginInfo(
                email,
                rawPass,
                dto.getPhoneNumber()
        );
    }


    public List<EmployeeListDto> getAllEmployees() {

        return employeeRepository.findAll()
                .stream()
                .map(requestMapper::toEmployeeDto)
                .toList();
    }



    public ApiResponse deactivateEmployee(Integer id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException("Employee not found"));

        employee.getUser().setStatus(UserStatus.INACTIVE);
        employeeRepository.save(employee);

        return new ApiResponse(true,"Employee deactivated");
    }

}
