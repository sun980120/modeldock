package com.modeldock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "dock.scheduling", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {}
