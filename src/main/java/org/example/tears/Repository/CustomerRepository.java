package org.example.tears.Repository;

import org.example.tears.Model.Customer;
import org.example.tears.DTO.AdminCouponCustomerDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository
        extends JpaRepository<Customer, Integer> {

    @Query("""
        SELECT new org.example.tears.DTO.AdminCouponCustomerDto(
            c.id,
            u.fullName,
            u.phoneNumber
        )
        FROM Customer c
        JOIN c.user u
        ORDER BY u.fullName ASC
        """)
    List<AdminCouponCustomerDto> findAllCustomersForCouponSelection();
}