package org.example.tears.Repository;

import org.example.tears.Model.Car;
import org.example.tears.Model.CarModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarRepository extends JpaRepository<Car,Integer> {

    List<Car> findByCustomerId(Integer customerId);
    void deleteAllByCustomerId(Integer customerId);
}
