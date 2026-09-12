package org.example.tears.Repository;

import org.example.tears.Model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.example.tears.Model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository

public interface CouponRepository extends JpaRepository<Coupon, Integer> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    long countByActiveTrue();

    long countByActiveTrueAndExpiryDateGreaterThanEqualAndExpiryDateLessThanEqual(
            LocalDate start,
            LocalDate end
    );

    @Query("""
        SELECT c
        FROM Coupon c
        WHERE
            LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(c.title, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(c.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
    """)
    List<Coupon> searchCoupons(
            @Param("search") String search
    );
}