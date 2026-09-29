package com.orderhub.backend.catalog;

import  org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {    
} 