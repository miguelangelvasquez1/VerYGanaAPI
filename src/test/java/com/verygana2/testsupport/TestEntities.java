package com.verygana2.testsupport;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.verygana2.models.Avatar;
import com.verygana2.models.Category;
import com.verygana2.models.Department;
import com.verygana2.models.Municipality;
import com.verygana2.models.User;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.EntityManager;

/**
 * Fábrica de entidades para tests de integración.
 * Construye el grafo mínimo que exige ConsumerDetails
 * (Department → Municipality, Avatar, Category, User).
 */
public final class TestEntities {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private TestEntities() {
    }

    /**
     * Persiste las entidades de catálogo compartidas y retorna un consumer listo.
     * Cada llamada genera email/teléfono/hash únicos.
     */
    public static ConsumerDetails persistConsumer(EntityManager em) {
        ConsumerDetails consumer = newConsumer(em);
        em.persist(consumer);
        em.flush();
        return consumer;
    }

    /**
     * Construye un consumer SIN persistirlo (sus dependencias sí quedan
     * persistidas). Útil para probar flujos de registro como
     * ReferralService.prepareNewConsumer.
     */
    public static ConsumerDetails newConsumer(EntityManager em) {
        long n = SEQ.getAndIncrement();

        Department department = em.find(Department.class, "63");
        if (department == null) {
            department = new Department();
            department.setCode("63");
            department.setName("Quindío");
            em.persist(department);
        }

        Municipality municipality = em.find(Municipality.class, "63001");
        if (municipality == null) {
            municipality = new Municipality();
            municipality.setCode("63001");
            municipality.setName("Armenia");
            municipality.setDepartment(department);
            em.persist(municipality);
        }

        Avatar avatar = new Avatar();
        avatar.setName("avatar-test-" + n);
        avatar.setImageUrl("https://cdn.test/avatar.png");
        em.persist(avatar);

        Category category = new Category();
        category.setName("categoria-test-" + n);
        em.persist(category);

        User user = new User();
        user.setEmail("consumer" + n + "@test.com");
        user.setPhoneNumber("300000" + String.format("%04d", n));
        user.setPassword("hash");
        user.setRole(Role.CONSUMER);
        user.setUserState(UserState.ACTIVE);
        user.setRegisteredDate(ZonedDateTime.now());
        em.persist(user);

        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setUser(user);
        consumer.setUserHash("hash-" + n);
        consumer.setUserName("user" + n);
        consumer.setAvatar(avatar);
        consumer.setName("Nombre" + n);
        consumer.setLastName("Apellido" + n);
        consumer.setDepartmentName("Quindío");
        consumer.setMunicipalityName("Armenia");
        consumer.setMunicipality(municipality);
        consumer.setCategories(List.of(category));
        consumer.setReferralCode("CODE" + String.format("%04d", n));
        consumer.setDocumentType(DocumentType.CC);
        consumer.setDocumentNumber("100000" + n);

        em.flush();
        return consumer;
    }
}