# Spring RSocket Security with TLS

This guide will help you configure TLS with your new or existing RSocket services using Spring Boot 2.7.x. We will review a few options for generating the cerficate, as well as importing and utilizing that certificate in your Spring Boot application.

It is assumed the developer knows about OpenSSL, Kotlin or at least JAVA 11 and uses Spring Boot. Dont worry if you're new to RSocket for Spring Boot or Reactive streams. All of the TLS security concerns relate to the Transport, not the protocol itself. Of course, the best place to understand are [the reference docs](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#appendix.application-properties.rsocket), so please read them!
