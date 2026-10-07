package com.orderhub.backend.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Productos de demostración. Desactivado por defecto: se activa con
 * {@code app.demo-data=true} (variable APP_DEMO_DATA) o con el perfil {@code demo}.
 * Idempotente por nombre de producto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    record Demo(String name, String description, long price, int stock) {
    }

    static final List<Demo> PRODUCTS = List.of(
            new Demo("Café de origen Huila 500 g", "Café especial tostado medio, notas a panela y cítricos.", 38900, 40),
            new Demo("Aguacate Hass (kg)", "Aguacate Hass de Antioquia, listo para consumir en 2-3 días.", 14500, 60),
            new Demo("Panela orgánica 1 kg", "Panela pulverizada orgánica, sin aditivos.", 9800, 80),
            new Demo("Chocolate de mesa 500 g", "Chocolate amargo 70 % cacao colombiano, 12 pastillas.", 15900, 35),
            new Demo("Audífonos Bluetooth Pulse", "Inalámbricos con cancelación de ruido y 30 h de batería.", 189900, 12),
            new Demo("Teclado mecánico K65", "Switches rojos, retroiluminado, disposición en español.", 259000, 8),
            new Demo("Mouse ergonómico inalámbrico", "Recargable por USB-C, 6 botones y 3 niveles de DPI.", 99900, 25),
            new Demo("Termo acero inoxidable 750 ml", "Mantiene el frío 24 h y el calor 12 h. Libre de BPA.", 69900, 4),
            new Demo("Mochila urbana impermeable", "Compartimento para portátil de 15 pulgadas y puerto de carga USB.", 134900, 3),
            new Demo("Cuaderno argollado 100 hojas", "Pasta dura, hojas rayadas de 75 g.", 12500, 150),
            new Demo("Lámpara de escritorio LED", "Tres temperaturas de color y brillo regulable.", 84900, 18),
            new Demo("Tablet de dibujo Sketch M", "Área activa de 10 pulgadas, lápiz sin batería. Agotado temporalmente.", 329000, 0));

    private final ProductRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        long created = PRODUCTS.stream().filter(this::createIfMissing).count();
        log.info("Demo data: {} products created, {} already existed", created, PRODUCTS.size() - created);
    }

    private boolean createIfMissing(Demo demo) {
        if (repository.existsByName(demo.name())) {
            return false;
        }
        Product product = new Product();
        product.setName(demo.name());
        product.setDescription(demo.description());
        product.setPrice(BigDecimal.valueOf(demo.price()));
        product.setStock(demo.stock());
        repository.save(product);
        return true;
    }
}
