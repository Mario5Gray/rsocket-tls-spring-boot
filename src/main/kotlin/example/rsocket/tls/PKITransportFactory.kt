package example.rsocket.tls

import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.TcpServerTransport
import reactor.netty.tcp.TcpClient
import reactor.netty.tcp.TcpServer
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

open class PKITransportFactory(private val trustFile: File,
                               private val keyFile: File,
                               private val jksPass: String) {

//    fun tcpServerTransport(host: String, port: int): TcpServerTransport {
//        TcpServerTransport
//                .create(TcpServer.create().port(9090).secure { ssl ->
//                    ssl.sslContext(
//                            SslContextBuilder.forServer(
//
//                            )
//                    )
//                })
//    }

    fun tcpClientTransport(host: String, port: Int): TcpClientTransport {

        val trustManager = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply {
                    val ks = KeyStore.getInstance("JKS").apply {
                        this.load(FileInputStream(trustFile), jksPass.toCharArray())
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
                                            .keyStoreType("JKS")
                                            .trustManager(trustManager)
                                            .build()
                            )
                        })
    }
}