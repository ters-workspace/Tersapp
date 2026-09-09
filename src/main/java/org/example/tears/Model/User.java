package org.example.tears.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.example.tears.Enums.UserRole;
import org.example.tears.Enums.UserStatus;


@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 70, message = "First name must be between 3 and 20 characters")
    @Column(nullable = false)
    private String fullName;
    @Email
    @Column(nullable = true, unique = true)
    private String email;

    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$", message = "Password must be strong")
    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^\\+9665\\d{8}$", message = "Phone number must be a valid Saudi number")
    @Column(nullable = true, unique = true)
    private String phoneNumber;


    private Boolean notificationsEnabled = false;

    @Column(unique = true)
    private String pendingPhoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;


    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Customer customer;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "test_technician_id")
    private Employee testTechnician;

    @ManyToOne
    @JoinColumn(name = "test_pricing_id")
    private Employee testPricing;

}
