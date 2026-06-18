package ch.swissqcommerce.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "ch.swissqcommerce.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class HexagonalArchitectureTest {

    @ArchTest
    public static final ArchRule domainCoreShouldNotDependOnAdapters =
            noClasses()
                    .that()
                    .resideInAPackage("ch.swissqcommerce.backend.domain..core..")
                    .and()
                    .resideOutsideOfPackage("ch.swissqcommerce.backend.domain.event..")
                    .and()
                    .resideOutsideOfPackage("ch.swissqcommerce.backend.domain.transaction..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..adapter..")
                    .as("Domain core packages must not depend on any adapter packages");

    @ArchTest
    public static final ArchRule domainPortsShouldNotDependOnAdapters =
            noClasses()
                    .that()
                    .resideInAPackage("ch.swissqcommerce.backend.domain..port..")
                    .and()
                    .resideOutsideOfPackage("ch.swissqcommerce.backend.domain.event..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..adapter..")
                    .as("Domain port packages must not depend on any adapter packages");

    @ArchTest
    public static final ArchRule adaptersShouldNotDependOnCoreServices =
            noClasses()
                    .that()
                    .resideInAPackage("ch.swissqcommerce.backend.domain..adapter..")
                    .and()
                    .resideOutsideOfPackage("ch.swissqcommerce.backend.domain.agent.adapter..")
                    .and()
                    .resideOutsideOfPackage("ch.swissqcommerce.backend.domain.reward.adapter..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("ch.swissqcommerce.backend.domain..core.service..")
                    .as("Adapters must not depend on domain core service implementations directly");

    @ArchTest
    public static final ArchRule agentAdaptersShouldNotDependOnCoreServices =
            noClasses()
                    .that()
                    .resideInAPackage("ch.swissqcommerce.backend.domain.agent.adapter..")
                    .and()
                    .haveNameNotMatching(".*AgentController")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("ch.swissqcommerce.backend.domain.agent.core.service..")
                    .as(
                            "Agent adapters must not depend on domain core service implementations"
                                    + " directly, except AgentController");

    @ArchTest
    public static final ArchRule rewardAdaptersShouldNotDependOnCoreServices =
            noClasses()
                    .that()
                    .resideInAPackage("ch.swissqcommerce.backend.domain.reward.adapter..")
                    .and()
                    .haveNameNotMatching(".*RewardsListener")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("ch.swissqcommerce.backend.domain.reward.core.service..")
                    .as(
                            "Reward adapters must not depend on domain core service implementations"
                                    + " directly, except RewardsListener");

    @ArchTest
    public static final ArchRule schedulerShouldNotDependOnCoreServicesDirectly =
            noClasses()
                    .that()
                    .resideInAPackage("..scheduler..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("ch.swissqcommerce.backend.domain.governance.core.service..")
                    .as("Scheduler packages must not depend on domain governance core service implementations directly");
}
