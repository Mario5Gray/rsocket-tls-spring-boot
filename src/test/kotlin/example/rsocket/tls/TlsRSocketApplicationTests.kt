package example.rsocket.tls

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.test.StepVerifier

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TlsRSocketApplicationTests {

	@Autowired
	private lateinit var secureConnection: PKITransportFactory

	private lateinit var requester: RSocketRequester

	@BeforeAll
	fun setupOnce(@Value("\${spring.rsocket.server.port}") port: Int) {
		//val insecureConn = TestInsecureSecureConnection().tcpClientTransport("localhost", port)
		val securecon = secureConnection.tcpClientTransport("localhost", port)
		requester = RSocketRequester.builder().transport(securecon)

	}

	@Test
	fun contextLoads() {
	}

	@Test
	fun testRequestResponse() {
		val req = requester
				.route("status")
				.retrieveMono<String>()

		StepVerifier
				.create(req)
				.assertNext {
					Assertions
							.assertThat(it)
							.isNotNull
				}
				.verifyComplete()
	}

}
