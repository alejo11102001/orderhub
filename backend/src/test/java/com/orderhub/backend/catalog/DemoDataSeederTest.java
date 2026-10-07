package com.orderhub.backend.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock ProductRepository repository;

    @Test
    void catalogHasUniqueNamesAndSensibleData() {
        assertThat(DemoDataSeeder.PRODUCTS).hasSize(12);
        assertThat(DemoDataSeeder.PRODUCTS.stream().map(d -> d.name()).distinct()).hasSize(12);
        assertThat(DemoDataSeeder.PRODUCTS).allSatisfy(p -> {
            assertThat(p.price()).isPositive();
            assertThat(p.stock()).isNotNegative();
            assertThat(p.name().length()).isLessThanOrEqualTo(150);
        });
    }

    @Test
    void createsAllWhenEmpty() {
        when(repository.existsByName(anyString())).thenReturn(false);

        new DemoDataSeeder(repository).run(null);

        verify(repository, times(12)).save(any(Product.class));
    }

    @Test
    void skipsExistingProducts() {
        when(repository.existsByName(anyString())).thenReturn(true);

        new DemoDataSeeder(repository).run(null);

        verify(repository, never()).save(any());
    }
}
