package com.matbakhy.app.mfa;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MfaProperties.class)
class MfaConfiguration {
}
