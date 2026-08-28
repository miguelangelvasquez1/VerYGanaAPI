package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.Department;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para ProductRepository. Cubre los
 * conteos/existencia usados por reglas de negocio, el catálogo público
 * (findAllActiveProducts, searchProducts), la gestión del comercial
 * (findByCommercialId, findGameRewardsProducts) y las tareas de
 * administración/purga (findAllProductsForAdmin, findPurgeableProducts).
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:product-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProductRepository (integración H2)")
class ProductRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ProductRepository productRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    private ProductCategory persistCategory(String name) {
        ProductCategory category = new ProductCategory();
        category.setName(name);
        em.persist(category);
        em.flush();
        return category;
    }

    /**
     * Persiste un producto con el status deseado. Product.onCreate() fuerza
     * status=PENDING al persistir por primera vez, así que si se pide otro
     * status se hace un segundo flush actualizándolo explícitamente.
     */
    private Product persistProduct(CommercialDetails commercial, ProductCategory category, String name,
            long priceCents, ProductStatus status) {
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName(name);
        product.setDescription("Descripción de " + name);
        product.setPriceCents(priceCents);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();

        if (status != null && status != ProductStatus.PENDING) {
            product.setStatus(status);
            em.flush();
        }
        return product;
    }

    private ProductImageAsset persistImageAsset(Product product, String objectKey) {
        ProductImageAsset asset = new ProductImageAsset();
        asset.setObjectKey(objectKey);
        asset.setSizeBytes(1024L);
        asset.setStatus(AssetStatus.VALIDATED);
        asset.setProduct(product);
        em.persist(asset);
        em.flush();
        return asset;
    }

    private Municipality persistMunicipality(String code, String name, String deptCode, String deptName) {
        Department department = em.find(Department.class, deptCode);
        if (department == null) {
            department = new Department();
            department.setCode(deptCode);
            department.setName(deptName);
            em.persist(department);
        }
        Municipality municipality = em.find(Municipality.class, code);
        if (municipality == null) {
            municipality = new Municipality();
            municipality.setCode(code);
            municipality.setName(name);
            municipality.setDepartment(department);
            em.persist(municipality);
        }
        em.flush();
        return municipality;
    }

    private TargetAudience persistTargetAudience(Municipality... municipalities) {
        TargetAudience ta = TargetAudience.builder()
                .targetMunicipalities(new ArrayList<>(List.of(municipalities)))
                .build();
        em.persist(ta);
        em.flush();
        return ta;
    }

    /**
     * Bypasea el @PreUpdate de Product (que sobreescribiría updatedAt a
     * "ahora" en cualquier flush normal vía setter) para poder simular
     * productos "viejos" en findPurgeableProducts. Un UPDATE JPQL masivo no
     * dispara los callbacks de ciclo de vida de la entidad.
     */
    private void forceUpdatedAt(Product product, ZonedDateTime updatedAt) {
        em.createQuery("UPDATE Product p SET p.updatedAt = :updatedAt WHERE p.id = :id")
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", product.getId())
                .executeUpdate();
        em.clear();
    }

    /** Mismo motivo que forceUpdatedAt, para poder ordenar findAllProductsForAdmin de forma determinista. */
    private void forceCreatedAt(Product product, ZonedDateTime createdAt) {
        em.createQuery("UPDATE Product p SET p.createdAt = :createdAt WHERE p.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", product.getId())
                .executeUpdate();
        em.clear();
    }

    // ==================== countByCommercialIdAndIsActive ====================

    @Nested
    @DisplayName("countByCommercialIdAndIsActive")
    class CountByCommercialIdAndIsActive {

        @Test
        @DisplayName("cuenta solo los productos ACTIVE del comercial dado")
        void countsOnlyActiveProductsOfCommercial() {
            CommercialDetails commercialA = TestEntities.persistCommercial(em);
            CommercialDetails commercialB = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria conteo activos");

            persistProduct(commercialA, category, "Activo A1", 10000, ProductStatus.ACTIVE);
            persistProduct(commercialA, category, "Activo A2", 10000, ProductStatus.ACTIVE);
            persistProduct(commercialA, category, "Pendiente A3", 10000, ProductStatus.PENDING);
            persistProduct(commercialB, category, "Activo B1", 10000, ProductStatus.ACTIVE);

            assertThat(productRepository.countByCommercialIdAndIsActive(commercialA.getId())).isEqualTo(2);
            assertThat(productRepository.countByCommercialIdAndIsActive(commercialB.getId())).isEqualTo(1);
        }
    }

    // ==================== existsByIdAndCommercialId / existsByProductCategoryId / findByIdAndCommercialId ====================

    @Nested
    @DisplayName("existsByIdAndCommercialId, existsByProductCategoryId y findByIdAndCommercialId")
    class ExistsAndFindByOwnership {

        @Test
        @DisplayName("existsByIdAndCommercialId solo es true bajo el comercial dueño")
        void existsOnlyUnderOwningCommercial() {
            CommercialDetails owner = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria ownership");
            Product product = persistProduct(owner, category, "Producto propiedad", 10000, ProductStatus.ACTIVE);

            assertThat(productRepository.existsByIdAndCommercialId(product.getId(), owner.getId())).isTrue();
            assertThat(productRepository.existsByIdAndCommercialId(product.getId(), other.getId())).isFalse();
        }

        @Test
        @DisplayName("existsByProductCategoryId detecta si la categoría tiene productos asociados")
        void existsByCategoryDetectsAssociatedProducts() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory withProducts = persistCategory("Categoria con productos");
            ProductCategory empty = persistCategory("Categoria vacía");
            persistProduct(commercial, withProducts, "Producto en categoria", 10000, ProductStatus.ACTIVE);

            assertThat(productRepository.existsByProductCategoryId(withProducts.getId())).isTrue();
            assertThat(productRepository.existsByProductCategoryId(empty.getId())).isFalse();
        }

        @Test
        @DisplayName("findByIdAndCommercialId retorna el producto solo bajo el comercial dueño")
        void findsProductOnlyUnderOwningCommercial() {
            CommercialDetails owner = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria find ownership");
            Product product = persistProduct(owner, category, "Producto find", 10000, ProductStatus.ACTIVE);

            Optional<Product> found = productRepository.findByIdAndCommercialId(product.getId(), owner.getId());
            Optional<Product> notFound = productRepository.findByIdAndCommercialId(product.getId(), other.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Producto find");
            assertThat(notFound).isEmpty();
        }
    }

    // ==================== findAllActiveProducts ====================

    @Nested
    @DisplayName("findAllActiveProducts")
    class FindAllActiveProducts {

        @Test
        @DisplayName("trae solo productos ACTIVE con categoría fetch-eada, incluso sin imagen (LEFT JOIN)")
        void returnsOnlyActiveWithCategoryFetchedRegardlessOfImage() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria catálogo");

            Product withImage = persistProduct(commercial, category, "Producto con imagen", 10000, ProductStatus.ACTIVE);
            persistImageAsset(withImage, "img-catalogo-1.png");

            Product withoutImage = persistProduct(commercial, category, "Producto sin imagen", 10000, ProductStatus.ACTIVE);

            persistProduct(commercial, category, "Producto pendiente catálogo", 10000, ProductStatus.PENDING);

            em.clear();

            Page<Product> page = productRepository.findAllActiveProducts(PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(Product::getId)
                    .containsExactlyInAnyOrder(withImage.getId(), withoutImage.getId());
            assertThat(page.getContent())
                    .extracting(p -> p.getProductCategory().getName())
                    .containsOnly("Categoria catálogo");
        }
    }

    // ==================== searchProducts / searchProductsInternal ====================

    /**
     * HALLAZGO (no corregido aquí — no se deben modificar archivos de
     * producción en este lote): searchProductsInternal navega
     * {@code p.targetAudience.targetMunicipalities} en el ORDER BY sin un
     * LEFT JOIN explícito (a diferencia de RaffleRepository.findActiveRaffles
     * /findLiveRaffles, que sí declara "LEFT JOIN r.targetAudience ta").
     * Hibernate resuelve esa navegación implícita como INNER JOIN hacia
     * target_audiences, así que CUALQUIER producto sin TargetAudience
     * asignado —el caso más común, ya que el campo es opcional— queda
     * excluido de TODOS los resultados de búsqueda, con o sin filtro de
     * municipio. Esto contradice el javadoc del método ("el municipio solo
     * prioriza, nunca excluye"). Ver el test dedicado
     * productsWithoutTargetAudienceAreIncorrectlyExcluded() más abajo.
     *
     * Para poder ejercer el resto de los filtros de búsqueda sin toparse con
     * este bug, los productos de estos tests reciben una TargetAudience
     * "abierta" (sin municipios) vía este helper.
     */
    @Nested
    @DisplayName("searchProducts / searchProductsInternal")
    class SearchProducts {

        private Product persistSearchableProduct(CommercialDetails commercial, ProductCategory category,
                String name, long priceCents, ProductStatus status) {
            Product product = persistProduct(commercial, category, name, priceCents, status);
            product.setTargetAudience(persistTargetAudience());
            em.persist(product);
            em.flush();
            return product;
        }

        private Product persistSearchableProduct(CommercialDetails commercial, ProductCategory category,
                String name, long priceCents) {
            return persistSearchableProduct(commercial, category, name, priceCents, ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("sin filtros trae todos los productos ACTIVE")
        void noFiltersReturnsAllActive() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria búsqueda sin filtros");
            Product active = persistSearchableProduct(commercial, category, "Producto activo sin filtros", 10000);
            persistSearchableProduct(commercial, category, "Producto pendiente sin filtros", 10000,
                    ProductStatus.PENDING);

            Page<Product> page = productRepository.searchProducts(null, null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(active.getId());
        }

        @Test
        @DisplayName("filtra por texto de búsqueda que coincide con el nombre, sin distinguir mayúsculas")
        void filtersBySearchQueryInName() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria búsqueda nombre");
            Product target = persistSearchableProduct(commercial, category, "Audífonos Bluetooth Pro", 10000);
            persistSearchableProduct(commercial, category, "Otro producto distinto", 10000);

            Page<Product> page = productRepository.searchProducts("audífonos", null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("filtra por texto de búsqueda que coincide con la descripción")
        void filtersBySearchQueryInDescription() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria búsqueda descripción");
            Product target = persistSearchableProduct(commercial, category, "Producto X", 10000);
            target.setDescription("Contiene la palabra clave especialisima");
            em.flush();
            persistSearchableProduct(commercial, category, "Producto Y", 10000);

            Page<Product> page = productRepository.searchProducts("especialisima", null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("filtra por texto de búsqueda que coincide con el nombre de la empresa (commercial)")
        void filtersBySearchQueryInCommercialCompanyName() {
            CommercialDetails matchingCommercial = TestEntities.persistCommercial(em);
            matchingCommercial.setCompanyName("Empresa Buscadísima SAS");
            em.flush();
            CommercialDetails otherCommercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria búsqueda empresa");

            Product target = persistSearchableProduct(matchingCommercial, category, "Producto empresa buscada",
                    10000);
            persistSearchableProduct(otherCommercial, category, "Producto otra empresa", 10000);

            Page<Product> page = productRepository.searchProducts("buscadísima", null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("filtra por texto de búsqueda que coincide con el nombre de la categoría")
        void filtersBySearchQueryInCategoryName() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory matchingCategory = persistCategory("Categoria Electrónica Especial");
            ProductCategory otherCategory = persistCategory("Categoria Hogar");

            Product target = persistSearchableProduct(commercial, matchingCategory,
                    "Producto en categoría especial", 10000);
            persistSearchableProduct(commercial, otherCategory, "Producto en categoría hogar", 10000);

            Page<Product> page = productRepository.searchProducts("electrónica especial", null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("filtra por categoría exacta cuando se especifica productCategoryId")
        void filtersByProductCategoryId() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory categoryA = persistCategory("Categoria A filtro");
            ProductCategory categoryB = persistCategory("Categoria B filtro");
            Product inA = persistSearchableProduct(commercial, categoryA, "Producto en A", 10000);
            persistSearchableProduct(commercial, categoryB, "Producto en B", 10000);

            Page<Product> page = productRepository.searchProducts(null, categoryA.getId(), null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(inA.getId());
        }

        @Test
        @DisplayName("filtra por rating mínimo (averageRate >= minRating)")
        void filtersByMinRating() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria rating");
            Product highRated = persistSearchableProduct(commercial, category, "Producto bien calificado", 10000);
            highRated.setAverageRate(4.5);
            em.flush();
            Product lowRated = persistSearchableProduct(commercial, category, "Producto mal calificado", 10000);
            lowRated.setAverageRate(1.0);
            em.flush();

            Page<Product> page = productRepository.searchProducts(null, null, 4.0, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(highRated.getId());
        }

        @Test
        @DisplayName("filtra por precio máximo (priceCents <= maxPriceCents)")
        void filtersByMaxPriceCents() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria precio");
            Product cheap = persistSearchableProduct(commercial, category, "Producto barato", 5000);
            persistSearchableProduct(commercial, category, "Producto caro", 500000);

            Page<Product> page = productRepository.searchProducts(null, null, null, 10000L, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(cheap.getId());
        }

        @Test
        @DisplayName("entre productos que SÍ tienen TargetAudience, el municipio prioriza el orden sin excluir al dirigido a otro municipio")
        void municipalityPrioritizesAmongProductsWithTargetAudience() {
            Municipality armenia = persistMunicipality("63001", "Armenia", "63", "Quindío");
            Municipality medellin = persistMunicipality("05001", "Medellín", "05", "Antioquia");

            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria municipio");

            Product targetedToMedellin = persistProduct(commercial, category, "Producto dirigido a Medellín", 10000,
                    ProductStatus.ACTIVE);
            targetedToMedellin.setTargetAudience(persistTargetAudience(medellin));
            em.persist(targetedToMedellin);
            em.flush();

            // TargetAudience "abierta" (sin municipios): debe tratarse como afín a
            // cualquier municipio, igual que en el CASE de la query.
            Product openTarget = persistSearchableProduct(commercial, category, "Producto sin municipio dirigido",
                    10000);

            Page<Product> resultForArmenia = productRepository.searchProducts(null, null, null, null, armenia,
                    PageRequest.of(0, 10));

            // El producto dirigido a OTRO municipio (Medellín) sigue apareciendo
            // cuando se busca desde Armenia: el municipio solo reordena.
            assertThat(resultForArmenia.getContent())
                    .extracting(Product::getId)
                    .containsExactlyInAnyOrder(targetedToMedellin.getId(), openTarget.getId());

            // El de audiencia abierta (afín a cualquier municipio) se lista antes
            // que el dirigido explícitamente a un municipio distinto al buscado.
            assertThat(resultForArmenia.getContent().get(0).getId()).isEqualTo(openTarget.getId());
        }

        @Test
        @DisplayName("HALLAZGO: un producto ACTIVE sin TargetAudience asignado (el caso más común) queda excluido de la búsqueda por un INNER JOIN implícito")
        void productsWithoutTargetAudienceAreIncorrectlyExcluded() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria bug target audience");
            // Producto ACTIVE normal, sin TargetAudience asignado — el campo es
            // nullable/opcional según Product.targetAudience.
            persistProduct(commercial, category, "Producto activo sin target audience", 10000, ProductStatus.ACTIVE);

            Page<Product> page = productRepository.searchProducts(null, null, null, null, null,
                    PageRequest.of(0, 10));

            // Comportamiento REAL actual (bug, ver comentario de la clase): debería
            // aparecer según el javadoc del método, pero la navegación implícita a
            // p.targetAudience en el ORDER BY genera un INNER JOIN hacia
            // target_audiences y lo excluye.
            assertThat(page.getContent()).isEmpty();
        }
    }

    // ==================== findByCommercialId (Page y List) ====================

    @Nested
    @DisplayName("findByCommercialId (Page y List)")
    class FindByCommercialId {

        @Test
        @DisplayName("ambos overloads traen solo los productos ACTIVE del comercial dado")
        void bothOverloadsReturnOnlyActiveProductsOfCommercial() {
            CommercialDetails commercialA = TestEntities.persistCommercial(em);
            CommercialDetails commercialB = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria findByCommercialId");

            persistProduct(commercialA, category, "Activo A1 comercial", 10000, ProductStatus.ACTIVE);
            persistProduct(commercialA, category, "Activo A2 comercial", 10000, ProductStatus.ACTIVE);
            persistProduct(commercialA, category, "Pendiente A3 comercial", 10000, ProductStatus.PENDING);
            persistProduct(commercialB, category, "Activo B1 comercial", 10000, ProductStatus.ACTIVE);

            Page<Product> page = productRepository.findByCommercialId(commercialA.getId(), PageRequest.of(0, 10));
            List<Product> list = productRepository.findByCommercialId(commercialA.getId());

            assertThat(page.getContent()).hasSize(2);
            assertThat(list).hasSize(2);
            assertThat(list).extracting(Product::getStatus).containsOnly(ProductStatus.ACTIVE);
        }
    }

    // ==================== findGameRewardsProducts / countGameRewards ====================

    @Nested
    @DisplayName("findGameRewardsProducts y countGameRewards")
    class GameRewards {

        @Test
        @DisplayName("findGameRewardsProducts limita a 3 resultados ACTIVE ordenados por nombre DESC; countGameRewards cuenta todos")
        void limitsToThreeOrderedByNameDescWhileCountReturnsAll() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria game rewards");

            for (String name : List.of("Reward A", "Reward B", "Reward C", "Reward D")) {
                Product reward = persistProduct(commercial, category, name, 10000, ProductStatus.ACTIVE);
                reward.setIsGameReward(true);
                em.flush();
            }
            Product pendingReward = persistProduct(commercial, category, "Reward pendiente", 10000,
                    ProductStatus.PENDING);
            pendingReward.setIsGameReward(true);
            em.flush();

            List<Product> topThree = productRepository.findGameRewardsProducts(commercial.getId());
            Integer count = productRepository.countGameRewards(commercial.getId());

            assertThat(topThree).hasSize(3);
            assertThat(topThree).extracting(Product::getName)
                    .containsExactly("Reward D", "Reward C", "Reward B");
            // El pendiente no cuenta: solo ACTIVE + isGameReward=true (4 en total).
            assertThat(count).isEqualTo(4);
        }
    }

    // ==================== countCommercialProducts ====================

    @Nested
    @DisplayName("countCommercialProducts")
    class CountCommercialProducts {

        @Test
        @DisplayName("cuenta los productos del comercial que coinciden exactamente con el status pedido")
        void countsProductsMatchingExactStatus() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria conteo status");

            persistProduct(commercial, category, "Activo status 1", 10000, ProductStatus.ACTIVE);
            persistProduct(commercial, category, "Pendiente status 1", 10000, ProductStatus.PENDING);
            persistProduct(commercial, category, "Pendiente status 2", 10000, ProductStatus.PENDING);

            assertThat(productRepository.countCommercialProducts(commercial.getId(), ProductStatus.ACTIVE))
                    .isEqualTo(1);
            assertThat(productRepository.countCommercialProducts(commercial.getId(), ProductStatus.PENDING))
                    .isEqualTo(2);
            assertThat(productRepository.countCommercialProducts(commercial.getId(), ProductStatus.REJECTED))
                    .isEqualTo(0);
        }
    }

    // ==================== findAllProductsForAdmin ====================

    @Nested
    @DisplayName("findAllProductsForAdmin")
    class FindAllProductsForAdmin {

        @Test
        @DisplayName("sin filtros trae todos los productos (cualquier status) ordenados por createdAt DESC")
        void noFiltersReturnsAllOrderedByCreatedAtDesc() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria admin orden");

            Product older = persistProduct(commercial, category, "Producto admin viejo", 10000, ProductStatus.ACTIVE);
            forceCreatedAt(older, now().minusDays(5));
            Product newer = persistProduct(commercial, category, "Producto admin nuevo", 10000,
                    ProductStatus.PENDING);
            forceCreatedAt(newer, now());

            Page<Product> page = productRepository.findAllProductsForAdmin(null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId)
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        @DisplayName("filtra por status cuando se especifica")
        void filtersByStatus() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria admin status");
            Product active = persistProduct(commercial, category, "Activo admin filtro", 10000, ProductStatus.ACTIVE);
            persistProduct(commercial, category, "Pendiente admin filtro", 10000, ProductStatus.PENDING);

            Page<Product> page = productRepository.findAllProductsForAdmin(ProductStatus.ACTIVE, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(active.getId());
        }

        @Test
        @DisplayName("filtra por texto de búsqueda en nombre de producto o empresa")
        void filtersBySearchInNameOrCompany() {
            CommercialDetails matchingCommercial = TestEntities.persistCommercial(em);
            matchingCommercial.setCompanyName("Empresa Admin Buscada SAS");
            em.flush();
            ProductCategory category = persistCategory("Categoria admin búsqueda");

            Product byCompany = persistProduct(matchingCommercial, category, "Producto cualquiera", 10000,
                    ProductStatus.ACTIVE);
            CommercialDetails other = TestEntities.persistCommercial(em);
            persistProduct(other, category, "Producto no relacionado", 10000, ProductStatus.ACTIVE);

            Page<Product> page = productRepository.findAllProductsForAdmin(null, "admin buscada",
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(byCompany.getId());
        }
    }

    // ==================== findPurgeableProducts ====================

    @Nested
    @DisplayName("findPurgeableProducts")
    class FindPurgeableProducts {

        @Test
        @DisplayName("trae productos REJECTED/INACTIVE cuyo updatedAt es anterior al umbral, excluyendo ACTIVE y los recientes")
        void returnsOldRejectedOrInactiveExcludingActiveAndRecent() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria purga");

            Product oldRejected = persistProduct(commercial, category, "Rechazado viejo", 10000,
                    ProductStatus.REJECTED);
            forceUpdatedAt(oldRejected, now().minusDays(60));

            Product oldInactive = persistProduct(commercial, category, "Inactivo viejo", 10000,
                    ProductStatus.INACTIVE);
            forceUpdatedAt(oldInactive, now().minusDays(60));

            Product recentRejected = persistProduct(commercial, category, "Rechazado reciente", 10000,
                    ProductStatus.REJECTED);
            forceUpdatedAt(recentRejected, now().minusDays(1));

            Product oldActive = persistProduct(commercial, category, "Activo viejo", 10000, ProductStatus.ACTIVE);
            forceUpdatedAt(oldActive, now().minusDays(60));

            ZonedDateTime threshold = now().minusDays(30);
            List<Product> purgeable = productRepository.findPurgeableProducts(threshold);

            assertThat(purgeable).extracting(Product::getId)
                    .containsExactlyInAnyOrder(oldRejected.getId(), oldInactive.getId());
        }
    }
}
