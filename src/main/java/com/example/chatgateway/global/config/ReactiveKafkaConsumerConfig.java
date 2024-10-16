package com.example.chatgateway.global.config;

import com.example.chatgateway.domain.dto.UserInfoDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// 인증 인스턴스에서 결과(UserInfoDTO)를 전달받기 위한 컨슈머
@EnableKafka
@Configuration
public class ReactiveKafkaConsumerConfig {

    @Value("${kafka.uri}")
    private String uri;

    @Value("${kafka.group-id}")
    private String groupId;

    @Value("${kafka.auto-offset-reset}")
    private String autoOffsetReset;

    @Value("${kafka.topic}")
    private String topic;

    @Bean
    public KafkaReceiver<String, UserInfoDTO> kafkaReceiver() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, uri);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, UserInfoDTO.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        ReceiverOptions<String, UserInfoDTO> receiverOptions = ReceiverOptions.<String, UserInfoDTO>create(props)
                .subscription(Collections.singletonList("auth"))
                .withValueDeserializer(new JsonDeserializer<>(UserInfoDTO.class, false))
                .commitInterval(Duration.ZERO) // 수동 커밋 사용
                .commitBatchSize(0); // 수동 커밋 크기 설정

        return KafkaReceiver.create(receiverOptions);
    }
}
