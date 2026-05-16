package com.example.hibernate.unnestbugs;

import static org.assertj.core.api.Assertions.assertThatNoException;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Struct;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Nested EXISTS over an array-of-structs-each-containing-an-array fails during HQL→SQM
 * conversion with {@code java.lang.ClassCastException: SqmFunctionJoin cannot be cast to
 * SqmSingularValuedJoin} from {@code SqmSubQuery.correlate(Join)}.
 *
 * <p>The outermost symptom is a missing branch in the correlation cast site, but
 * investigation (see the accompanying bug report) indicates the cast is only the first of
 * several layers blocking nested unnest. The reproducer tests assert end-to-end success of
 * the HQL forms; they will only transition to passing once the full chain is addressed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NestedUnnestCorrelationTest {

    SessionFactory sf;

    @Embeddable
    @Struct(name = "Tax")
    public static class Tax {
        public String code;
        public double rate;
    }

    @Embeddable
    @Struct(name = "LineItem")
    public static class LineItem {
        public String sku;
        public Tax[] taxes;
    }

    @Entity(name = "OrderEntity")
    public static class OrderEntity {
        @Id
        public int id;
        public LineItem[] lineItems;
    }

    @BeforeAll
    void setUp() {
        sf = SupportInfra.buildSessionFactory(OrderEntity.class);
    }

    @AfterAll
    void tearDown() {
        if (sf != null) sf.close();
    }

    @Test
    void nestedExistsOverNestedArray_implicitCollectionPath_succeeds() {
        // Natural HQL: FROM OrderEntity o WHERE EXISTS (
        //                SELECT 1 FROM o.lineItems a WHERE EXISTS (
        //                  SELECT 1 FROM a.taxes b WHERE b.code = 'VAT'))
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from OrderEntity o where exists (select 1 from o.lineItems a "
                                        + "where exists (select 1 from a.taxes b where b.code = 'VAT'))",
                                OrderEntity.class)
                        .getResultList()));
    }

    @Test
    void nestedExistsOverNestedArray_outerLateralUnnestForm_succeeds() {
        // Alternative: outer FROM uses explicit `lateral unnest(...)`; inner EXISTS stays
        // implicit. The fully-explicit form (lateral unnest inside the inner EXISTS too)
        // doesn't parse — see HqlGrammarLateralUnnestInSubqueriesTest.
        //   FROM OrderEntity o JOIN lateral unnest(o.lineItems) a WHERE EXISTS (
        //     SELECT 1 FROM a.taxes b WHERE b.code = 'VAT')
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from OrderEntity o join lateral unnest(o.lineItems) a "
                                        + "where exists (select 1 from a.taxes b where b.code = 'VAT')",
                                OrderEntity.class)
                        .getResultList()));
    }
}
