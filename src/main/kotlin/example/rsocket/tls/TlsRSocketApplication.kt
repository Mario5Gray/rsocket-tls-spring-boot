package example.rsocket.tls

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TlsRSocketApplication

fun main(args: Array<String>) {
	runApplication<TlsRSocketApplication>(*args)
}
