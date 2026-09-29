package com.orderhub.backend.catalog;

import com.orderhub.backend.catalog.dto.ProductRequest;
import com.orderhub.backend.catalog.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository repository;

    @InjectMocks
    ProductService service;

    @Test
    void create_savesAndReturnsProduct() {
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse result = service.create(
                new ProductRequest("Mouse", "Optico", new BigDecimal("50000"), 5));

        assertThat(result.name()).isEqualTo("Mouse");
        assertThat(result.stock()).isEqualTo(5);
    }

    @Test
    void findById_whenMissing_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void delete_whenExists_deletesIt() {
        Product product = new Product();
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        service.delete(1L);

        verify(repository).delete(product);
    }
}