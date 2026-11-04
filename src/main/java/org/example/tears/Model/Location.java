package org.example.tears.Model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Double lat;
    private Double lng;
    private String address;
    @Column(nullable = true)
    private String title;
    private String city;

    @ManyToOne
    private Customer customer;
}
