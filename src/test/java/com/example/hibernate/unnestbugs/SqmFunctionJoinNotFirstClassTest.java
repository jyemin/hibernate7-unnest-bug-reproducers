package com.example.hibernate.unnestbugs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.sql.SQLException;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Struct;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Bug: {@code SqmFunctionJoin} (the SQM node for an alias bound to a set-returning function
 * like {@code lateral unnest(...)}, including the basic-plural-attribute JOIN sugar that
 * desugars to one) is not first-class in Hibernate's SQM-to-SQL visitors. Two distinct
 * failure sites have been observed; both stem from the same omission.
 *
 * <p>Each test asserts the query reaches a healthy code path. With the bug present, all tests
 * fail with the specific exception named in their group's Javadoc. When the gap is fixed, the
 * tests transition from failing to passing.
 */
class SqmFunctionJoinNotFirstClassTest {

    /**
     * <b>Failure A — {@code SqmMappingModelHelper.resolveSqmPath}.</b> Bare
     * {@link AssertionError} (no message) when resolving a SQM path through a
     * {@code SqmFunctionJoin} alias. Demonstrated via four HQL forms that all trip the same
     * failure, against H2 + Hibernate 7.3.4.Final.
     */
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class ResolveSqmPath {

        SessionFactory sf;

        @Entity(name = "Item")
        public static class Item {
            @Id
            public int id;
            public int[] tags;
        }

        @BeforeAll
        void setUp() {
            sf = SupportInfra.buildSessionFactory(Item.class);
        }

        @AfterAll
        void tearDown() {
            if (sf != null) sf.close();
        }

        @Test
        void scalarJoin_sugarForm_withBodyPredicate_succeeds() {
            // Natural HQL: FROM Item i JOIN i.tags t WHERE t > 5
            assertThatNoException()
                    .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                    "from Item i join i.tags t where t > 5", Item.class)
                            .getResultList()));
        }

        @Test
        void scalarExists_implicitCollectionPath_succeeds() {
            // Natural HQL: FROM Item i WHERE EXISTS (SELECT 1 FROM i.tags t WHERE t > 5)
            assertThatNoException()
                    .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                    "from Item i where exists (select 1 from i.tags t where t > 5)", Item.class)
                            .getResultList()));
        }

        @Test
        void inSubquery_overImplicitCollectionPath_succeeds() {
            // Natural HQL: FROM Item i WHERE 5 IN (SELECT t FROM i.tags t)
            assertThatNoException()
                    .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                    "from Item i where 5 in (select t from i.tags t)", Item.class)
                            .getResultList()));
        }

        @Test
        void scalarJoin_lateralUnnest_withBodyPredicate_succeeds() {
            // Explicit `lateral unnest` form (same desugaring as the sugar JOIN):
            //   FROM Item i JOIN LATERAL unnest(i.tags) t WHERE t > 5
            assertThatNoException()
                    .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                    "from Item i join lateral unnest(i.tags) t where t > 5", Item.class)
                            .getResultList()));
        }
    }

    /**
     * <b>Failure B — correlation cast to {@code SqmSingularValuedJoin}.</b>
     * {@link ClassCastException}{@code : SqmFunctionJoin cannot be cast to SqmSingularValuedJoin}
     * when an inner EXISTS subquery's FROM references a collection-valued path on the outer
     * EXISTS's alias.
     *
     * <p>Uses {@code PostgreSQLDialect} (H2 lacks {@code @Struct} support) with a stub
     * {@code ConnectionProvider} — no live PostgreSQL server. Each test asserts the resulting
     * exception is a {@code SQLException} from the stub (i.e., SQM-to-SQL conversion completed
     * and Hibernate reached the JDBC layer). To fully verify end-to-end, swap the stub for a
     * real PostgreSQL connection.
     */
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class CorrelationCast {

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
            sf = SupportInfra.buildPostgresSessionFactory(OrderEntity.class);
        }

        @AfterAll
        void tearDown() {
            if (sf != null) sf.close();
        }

        @Test
        void nestedExistsOverNestedArray_implicitCollectionPath_reachesJdbcLayer() {
            // Natural HQL: FROM OrderEntity o WHERE EXISTS (
            //                SELECT 1 FROM o.lineItems a WHERE EXISTS (
            //                  SELECT 1 FROM a.taxes b WHERE b.code = 'VAT'))
            var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                            "from OrderEntity o where exists (select 1 from o.lineItems a "
                                    + "where exists (select 1 from a.taxes b where b.code = 'VAT'))",
                            OrderEntity.class)
                    .getResultList()));
            assertThat(thrown).isNotNull();
            assertThat(rootCauseOf(thrown))
                    .as("expected to reach JDBC layer (stub SQLException) — meaning SQM conversion "
                            + "succeeded; got: %s",
                            rootCauseOf(thrown))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("StubConnectionProvider");
        }

        @Test
        void nestedExistsOverNestedArray_outerLateralUnnestForm_reachesJdbcLayer() {
            // Alternative: outer FROM uses explicit `lateral unnest(...)`; inner EXISTS stays
            // implicit. The fully-explicit form (lateral unnest inside the inner EXISTS too)
            // doesn't parse — see HqlGrammarLateralUnnestInSubqueriesTest.
            //   FROM OrderEntity o JOIN lateral unnest(o.lineItems) a WHERE EXISTS (
            //     SELECT 1 FROM a.taxes b WHERE b.code = 'VAT')
            var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                            "from OrderEntity o join lateral unnest(o.lineItems) a "
                                    + "where exists (select 1 from a.taxes b where b.code = 'VAT')",
                            OrderEntity.class)
                    .getResultList()));
            assertThat(thrown).isNotNull();
            assertThat(rootCauseOf(thrown))
                    .as("expected to reach JDBC layer (stub SQLException); got: %s", rootCauseOf(thrown))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("StubConnectionProvider");
        }

        private Throwable rootCauseOf(Throwable t) {
            var c = t;
            while (c.getCause() != null && c.getCause() != c) {
                c = c.getCause();
            }
            return c;
        }
    }
}
