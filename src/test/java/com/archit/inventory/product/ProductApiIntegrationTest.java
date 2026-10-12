package com.archit.inventory.product;

import org.springframework.http.ProblemDetail;
import com.archit.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductApiIntegrationTest extends AbstractIntegrationTest {
    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void createAndGetProduct() {
        String sku = "SKU-" + UUID.randomUUID();

        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                url("/products"),
                new CreateProductRequest(sku, "Widget", 25),
                ProductResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProductResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.sku()).isEqualTo(sku);
        assertThat(created.name()).isEqualTo("Widget");
        assertThat(created.totalStock()).isEqualTo(25);
        assertThat(created.availableStock()).isEqualTo(25);
        assertThat(created.reservedStock()).isZero();
        assertThat(created.soldStock()).isZero();
        assertThat(created.totalStock()).isEqualTo(created.availableStock() + created.reservedStock() + created.soldStock());

        ResponseEntity<ProductResponse> getResponse = restTemplate.getForEntity(
                url("/products/" + created.id()),
                ProductResponse.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isEqualTo(created);
    }

    @Test
    void createRejectsDuplicateSku() {
        String sku = "SKU-" + UUID.randomUUID();
        ResponseEntity<ProductResponse> firstResponse = restTemplate.postForEntity(
                url("/products"),
                new CreateProductRequest(sku, "Widget", 10),
                ProductResponse.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ProblemDetail> duplicateResponse = restTemplate.postForEntity(
                url("/products"),
                new CreateProductRequest(sku, "Widget 2", 10),
                ProblemDetail.class
        );

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsInvalidStockAndBlankSku() {
        ResponseEntity<ProblemDetail> negativeStockResponse = restTemplate.postForEntity(
                url("/products"),
                new CreateProductRequest("SKU-" + UUID.randomUUID(), "Widget", -1),
                ProblemDetail.class
        );
        assertThat(negativeStockResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ProblemDetail> blankSkuResponse = restTemplate.postForEntity(
                url("/products"),
                new CreateProductRequest(" ", "Widget", 1),
                ProblemDetail.class
        );
        assertThat(blankSkuResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsOversizedFieldsWithoutExposingSql() {
        for (var request : new CreateProductRequest[] {
                new CreateProductRequest("X".repeat(129), "Widget", 1),
                new CreateProductRequest("SKU-" + UUID.randomUUID(), "X".repeat(256), 1) }) {
            var response = restTemplate.postForEntity(url("/products"), request, ProblemDetail.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getDetail()).doesNotContain("insert into", "SQL", "character varying");
        }
    }

    @Test
    void getRejectsUnknownProduct() {
        ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
                url("/products/" + UUID.randomUUID()),
                ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
