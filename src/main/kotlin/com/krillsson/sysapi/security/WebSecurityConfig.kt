package com.krillsson.sysapi.security

import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class WebSecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain? {
        http.requestCache { it.disable() }
        http.logout { it.disable() }
        http.csrf { it.disable() }
        http.servletApi { it.disable() }

        return http.authorizeHttpRequests { authorizationManagerRequestMatcherRegistry ->
            authorizationManagerRequestMatcherRegistry
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/**").authenticated()
        }
            .httpBasic(withDefaults())
            .build()
    }

    @Autowired
    fun configure(auth: AuthenticationManagerBuilder, yamlConfigFile: YAMLConfigFile, passwordEncoder: PasswordEncoder) {
        auth
            .inMemoryAuthentication()
            .withUser(yamlConfigFile.user.username)
            .password(passwordEncoder.encode(yamlConfigFile.user.password))
            .authorities("ROLE_USER")
    }
}