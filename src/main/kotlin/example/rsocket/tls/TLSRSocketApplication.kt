package example.rsocket.tls

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.io.File

@SpringBootApplication
class TLSRSocketApplication {

	@Bean
	fun secureClientConnection(@Value("\${spring.rsocket.client.ssl.trust-store}") trustStoreFile: File,
							   @Value("\${spring.rsocket.server.ssl.key-store-password}") jksPass: String): PKITransportFactory =
			PKITransportFactory(trustStoreFile, jksPass)
}

fun main(args: Array<String>) {
	runApplication<TLSRSocketApplication>(*args)
}
