package example.rsocket.tls

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono

@Controller
class ControllerMapping {
    @MessageMapping("status")
    fun status() = Mono.just(true)
}