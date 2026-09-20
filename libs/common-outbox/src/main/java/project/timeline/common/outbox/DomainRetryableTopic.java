package project.timeline.common.outbox;

import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.retry.annotation.Backoff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Единые параметры retry/DLT для доменных потребителей.
 *
 * <p>Три повтора с нарастающей задержкой (1s → 2s → 4s), затем DLT. Ошибки
 * десериализации сразу в DLT — повторять бессмысленно.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RetryableTopic(
		attempts = "4",
		backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
		dltStrategy = DltStrategy.FAIL_ON_ERROR,
		topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
		retryTopicSuffix = ".retry",
		dltTopicSuffix = ".dlt",
		exclude = {ConversionException.class, MessageConversionException.class,
				IllegalArgumentException.class}
)
public @interface DomainRetryableTopic {
}
