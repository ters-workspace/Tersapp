package org.example.tears.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.tears.Enums.EmployeeCity;
import org.example.tears.Enums.EmployeeRole;
import org.example.tears.Enums.JobTitle;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobTitle jobTitle;

    private Boolean mustChangePassword = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeRole employeeRole;

    @Column(unique = true)
    private String employeeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeCity city;

    @OneToOne
    @JoinColumn(name = "user_id",nullable = false)
    private User user;
}
