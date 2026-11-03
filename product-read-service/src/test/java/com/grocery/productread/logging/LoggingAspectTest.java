package com.grocery.productread.logging;

import ch.qos.logback.classic.Level;
import com.grocery.productread.logging.sample.SampleHelper;
import com.grocery.productread.logging.sample.SampleJob;
import com.grocery.productread.logging.sample.controller.SampleController;
import com.grocery.productread.logging.sample.messaging.consumer.SampleConsumer;
import com.grocery.productread.logging.sample.service.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LoggingAspect around small sample classes (src/test/.../logging/sample). They live in packages named like the real
 * layers (controller, service, messaging.consumer), so the same pointcuts match them. The proxy is made with
 * AspectJProxyFactory: the same proxy Spring creates for a bean, without starting Spring.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggingAspectTest {

    private final ch.qos.logback.classic.Logger aspectLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private Level originalLevel;

    @BeforeEach
    void showDebugLines() {
        originalLevel = aspectLogger.getLevel();
        aspectLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void cleanUp() {
        aspectLogger.setLevel(originalLevel);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void anApiCallIsLoggedWithTheHttpRequestAndTheDuration_butWithoutItsArguments(CapturedOutput output) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("POST", "/api/v1/things")));

        String result = withLogging(new SampleController()).create("my-secret-password");

        assertThat(result).isEqualTo("created");
        assertThat(output).contains("INFO").contains("API POST /api/v1/things SampleController.create OK in");
        assertThat(output).doesNotContain("my-secret-password").doesNotContain("created");
    }

    @Test
    void aFailedApiCallIsLogged_andTheExceptionIsPassedOnUnchanged(CapturedOutput output) {
        SampleController controller = withLogging(new SampleController());

        assertThatThrownBy(controller::fail).isInstanceOf(IllegalStateException.class).hasMessage("Out of stock");

        assertThat(output).contains("WARN").contains("API SampleController.fail FAILED in")
                .contains("IllegalStateException: Out of stock");
    }

    @Test
    void aSlowCallIsAWarning(CapturedOutput output) {
        withLogging(new SampleService()).slowWork();

        assertThat(output).contains("WARN").contains("SLOW INTERNAL SampleService.slowWork took").contains("(limit 100 ms)");
    }

    @Test
    void aFastInternalCallIsOnlyADebugLine(CapturedOutput output) {
        withLogging(new SampleService()).quickWork();

        assertThat(output).contains("DEBUG").contains("INTERNAL SampleService.quickWork OK in");
    }

    @Test
    void aFailingInternalCallIsLeftToTheApiEventOrJobLine(CapturedOutput output) {
        SampleService service = withLogging(new SampleService());

        assertThatThrownBy(service::fail).isInstanceOf(IllegalArgumentException.class);

        assertThat(output).doesNotContain("FAILED");
    }

    @Test
    void eventsAndJobsAreLoggedToo(CapturedOutput output) {
        SampleConsumer consumer = withLogging(new SampleConsumer());
        assertThatThrownBy(consumer::onEvent).isInstanceOf(IllegalStateException.class);
        withLogging(new SampleJob()).run();

        assertThat(output).contains("EVENT SampleConsumer.onEvent FAILED in").contains("JOB SampleJob.run OK in");
    }

    @Test
    void loggableAddsAnyOtherBean(CapturedOutput output) {
        withLogging(new SampleHelper()).help();

        assertThat(output).contains("INTERNAL SampleHelper.help OK in");
    }

    @SuppressWarnings("unchecked")
    private static <T> T withLogging(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new LoggingAspect(Duration.ofMillis(100)));
        return (T) factory.getProxy();
    }
}
