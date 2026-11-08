package net.onelitefeather.coloeus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.math.BigDecimal;
import net.onelitefeather.coloeus.money.AmountColumns;
import net.onelitefeather.coloeus.money.AmountJson;
import org.junit.jupiter.api.Test;

/**
 * Only {@link AmountColumns} and {@link AmountJson} may expose {@link BigDecimal} in a public signature.
 */
@AnalyzeClasses(packages = "net.onelitefeather.coloeus", importOptions = ImportOption.DoNotIncludeTests.class)
class MoneyBoundaryArchTest {

    private static final String SCOPE = "net.onelitefeather.coloeus..";

    @ArchTest
    static final ArchRule publicMethodsDoNotExposeBigDecimal = methods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().resideInAPackage(SCOPE)
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(AmountColumns.class.getName())
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(AmountJson.class.getName())
            .should(notExposeBigDecimalInCode());

    @ArchTest
    static final ArchRule publicConstructorsDoNotExposeBigDecimal = constructors()
            .that().arePublic()
            .and().areDeclaredInClassesThat().resideInAPackage(SCOPE)
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(AmountColumns.class.getName())
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(AmountJson.class.getName())
            .should(notExposeBigDecimalInCode());

    @ArchTest
    static final ArchRule publicFieldsDoNotExposeBigDecimal = fields()
            .that().arePublic()
            .and().areDeclaredInClassesThat().resideInAPackage(SCOPE)
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(AmountColumns.class.getName())
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(AmountJson.class.getName())
            .should(new ArchCondition<JavaField>("not have BigDecimal as type") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    if (isBigDecimal(field.getRawType())) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName() + " exposes BigDecimal"));
                    }
                }
            });

    @Test
    void ruleFailsForAPublicSignatureThatExposesBigDecimal() {
        JavaClasses leaking = new ClassFileImporter().importClasses(LeakingAmount.class);

        assertThrows(AssertionError.class,
                () -> methods().that().arePublic().should(notExposeBigDecimalInCode()).check(leaking),
                "a public method returning BigDecimal must violate the rule");
    }

    private static ArchCondition<JavaCodeUnit> notExposeBigDecimalInCode() {
        return new ArchCondition<>("not have BigDecimal in its signature") {
            @Override
            public void check(JavaCodeUnit unit, ConditionEvents events) {
                boolean exposed = unit.getParameters().stream().anyMatch(p -> isBigDecimal(p.getRawType()))
                        || (unit instanceof JavaMethod method
                        && isBigDecimal(method.getRawReturnType()));
                if (exposed) {
                    events.add(SimpleConditionEvent.violated(unit, unit.getFullName() + " exposes BigDecimal"));
                }
            }
        };
    }

    private static boolean isBigDecimal(JavaClass type) {
        return type.isEquivalentTo(BigDecimal.class);
    }

    /** Fixture for {@link #ruleFailsForAPublicSignatureThatExposesBigDecimal()}. */
    public static final class LeakingAmount {

        public BigDecimal value() {
            return BigDecimal.ONE;
        }
    }
}
