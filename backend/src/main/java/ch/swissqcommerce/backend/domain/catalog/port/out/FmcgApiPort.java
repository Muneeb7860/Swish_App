package ch.swissqcommerce.backend.domain.catalog.port.out;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface FmcgApiPort {
    Optional<FmcgProductDto> fetchProduct(String barcode);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class FmcgProductDto {
        private String name;
        private String brand;
    }
}
