package example.rsocket.tls

import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.netty.tcp.TcpClient
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

open class PKITransportFactory(private val trustFile: File,
                               private val storePass: String) {

    fun tcpClientTransport(host: String, port: Int): TcpClientTransport {

        val trustManager = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply {
                    val ks = KeyStore.getInstance("pkcs12")
                            .apply {
                        this.load(FileInputStream(trustFile), storePass.toCharArray())
                    }
                    this.init(ks)
                }

        return TcpClientTransport.create(
                TcpClient.create()
                        .host(host)
                        .port(port)
                        .secure { s ->
                            s.sslContext(
                                    SslContextBuilder
                                            .forClient()
                                            .keyStoreType("pkcs12")
                                            .trustManager(trustManager)
                                            .build()
                            )
                        })
    }
}