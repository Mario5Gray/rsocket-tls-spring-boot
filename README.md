# Spring RSocket Security with TLS

This guide will help you configure TLS with your new or existing RSocket services using Spring Boot 2.7.x. We will review a few options for generating the cerficate, as well as importing and utilizing that certificate in your Spring Boot application.

It is assumed the developer knows about OpenSSL, Kotlin or at least JAVA 11 and uses Spring Boot. Dont worry if you're new to RSocket for Spring Boot or Reactive streams. All of the TLS security concerns relate to the Transport, not the protocol itself. Of course, the best place to understand are [the reference docs](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#appendix.application-properties.rsocket), so please read them!

## Background on the PKI

SSL/TLS is a type of Public Key Infrastructure. PKI have concepts of a private and public key pair. Servers of an encrypted message will use the private key to generate the encrypted bits. Meanwhile, clients (non-trusted parties) can use the public portion to decrypt the message. TWriters keep the private key private, while distributing public keys to readers. 

Certificates allow a server to establish a `chain-of-trust` with clients and other entities involved in the exchange of encrypted messages. This is 

## Creating the Certificates

What is the procedure in creating a certificate? There are many steps here. First we need a Private Key, then we need to create a Certificate Signing Request (CSR) using that Private Key. Finally a third party Certificate Authority (CA) or our own enterprise will convert that CSR into a Certificate. This certificate should stay on the host that needs it. It should not get distributed to anyone else since at the logistics of managing a single self-signed key (that is untrusted by 3rd parties) is quite complex. 

Alternatively we will create the private key, then generate a CA Root certificate to establish the `chain-of-trust`. This Root certificate gets distributed to all devices and software that will eventually handle a key signed by it. In this way, we act like common CA's like Verisign and Globalsign. The process for each individual certificate in this case, will have our own Root certificate to sign (application specific) our CSRs. This way as single applications come and go, there is no need to re-distribute keys. 

Ultimately, we need to get the certificate(s) installed onto hosts in a format that our server/client/device can understand. Since we are securing an RSocket server, we have a couple options for that format: [JKS](https://vdc-download.vmware.com/vmwb-repository/dcr-public/93c0444e-a6cb-46a0-be25-b27a20f8c551/ac6ea73b-569b-4fff-80f1-e4144f541ac8/GUID-7FB0CDA2-BE63-49A4-B76C-BB806C3194AC.html) which is a JAVA specification for storing certificates, and [PKCS12](https://en.wikipedia.org/wiki/PKCS_12) which is the platform agnostic format.

In this guide we will explore exporting keystores in the PKCS#12 format, on the premise that JKS being deprecated at a later date.

### Generating our CA's Private Key

The Root certificate will be distributed to any client/servers/browsers/devices, while the individual certificates will get managed on the hosts that they belong to. This way the chain-of-trust is established with the Root CA, and -contrast to self-signed certificates- we will not have to redeploy a certificate for each known use-case.

To get started, we will need to generate the public/private key pair of our own CA. 

Lets generate an RSA 2048-bit key pair for our Root certificate:
```bash
openssl genrsa -out ca.key 2048 -nodes -sha256
```

This tells openssl to generate a new RSA key 2048 bits long and store it in a file `ca.key`. The resulting file is Base64 encoded.

You can even check and inspect the key:
```bash
openssl rsa -in ca.key -text -check
```

The output is somewhat lengthy and includes a status indicator of the key's validity, the HEXADECIMAL representation of the numeric key, and it's Base64 encoding. But next, we will use this key to create the Root certificate.

### Create the Root Certificate

Since we have the root key, we will create an X.509 certificate to be used in signing other keys.

Using openssl, create the root certificate from the key generated earlier:
```bash
openssl req -x509 -new -nodes -key ca.key -passout pass:111111 -out ca.cer -days 365 -sha256 -subj "/CN=CARoot"
```

The output is a file `ca.cer` containing the root X.509 certificate. Now for the fun part; lets create the server and client certificates signed by our Root CA!

### Create the Server Certificate

Now, we can begin the [CSR](https://en.wikipedia.org/wiki/Certificate_signing_request) process by generating a private key for the entity we wish to represent; In this case, an RSocket server. Let's generate the private key for the server:

```bash
openssl genrsa -out server.key 2048 -nodes -sha256
```

The CSR is a file containing a public key with some organizational metadata used to identify the originating entity. We will be the signing party that uses our Root certificate to sign a CSR. The resultant certificate will have the fingerprint of our Root, and establish a `chain-of-trust` with the server (or any other signed) certificate.

The CSR will contain information about the requesting entity including:

* Private Key
* FQDN - Fully Qualified Domain Name
* DN Owner - Distinguished Name of the owner entity
* DN Signer - Distinguished Name of the issuer entity
* Email
* Expiry - Time of expiry for the certificate

We largely ignore the details of metadata in this guide. However, in specific cases, metadata is expected to appear with a formalized value such as FQDN having the local root of a domain name.  This demo doesn't require such measures beyond Common Name (CN) matching our use case, thus we will use 'Unknown' for every other metadata and put 'Server'/'Client' as the CN.

We can now create the CSR file using the server's private key. The output is 'server.csr' containing the Base64 encoded Certificate Request:
```bash
openssl req -new -key server.key -sha256 -out server.csr -subj "/CN=server,OU=Unknown,O=Unknown,L=Unknown,ST=Unknown,C=Unknown"
```

Now, we can sign the server's CSR with our Root certificate.

## Signing the Server Certificate

We can apply the server's Private key, the server's CSR and the Root certificate to generate the server's certificate:

```bash
openssl x509 -req -CA ca.cer -CAkey ca.key -in server.csr -out server.pem -days 3650 -CAcreateserial -sha256
```

** Notes about this command

## Creating Key-stores 

Now, we need to insert this certificate into a keystore that our Java app can understand. We have a couple options for keystore format: 

* [JKS](https://vdc-download.vmware.com/vmwb-repository/dcr-public/93c0444e-a6cb-46a0-be25-b27a20f8c551/ac6ea73b-569b-4fff-80f1-e4144f541ac8/GUID-7FB0CDA2-BE63-49A4-B76C-BB806C3194AC.html) which is a JAVA specification for storing certificates. This format is probably going to get deprecated, so we'll use the next format.
* [PKCS12](https://en.wikipedia.org/wiki/PKCS_12) which is the platform agnostic format and widely adopted standard.

We will create a JKS keystore that contains our root and server certificates.

```bash
cat ca.cer server.pem > serverca.pem
openssl pkcs12 -export -in serverca.pem -inkey server.key -name localhost -password pass:111111 > server-ts.p12
```

If you already have your JKS keystore in tow, simply convert that keystore into PKCS12 using keytool:

```bash
keytool -importkeystore -srckeystore server-ts.jks -destkeystore server-ts.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass password -deststorepass password
```

Next, we will create a little RSocket server and apply TLS to it using our newly created keystore. We will explore the code options for both JKS and PKCS12.

### Create the client trust-store (keystore)

Usually, one will ship clients with just the Root certificate, as that will authenticate the server certificate per usual TLS fashion.
We can make one for the client by exporting the Root certificate into a pkcs12 'trust-store' keystore.

Create the client trust-store (keystore) file:
```bash
openssl pkcs12 -export -name localhost -in ca.cer -inkey ca.key -password pass:111111 > client-ts.p12
```

## A Spring Boot application

The server is a simple RSocket process. It only needs to respond to a message endpoint, and be encrypted along the way. Lets begin at [start.spring.io](https://start.spring.io) and add code bits from there.

Just one dependency - 'RSocket Messaging' is needed to get this going. Create the project and open it in your IDE. Otherwise, browse the [source code](https://github.com/Mario5Gray/rsocket-tls-spring-boot) here.

![app start](images/start-spring-io.png)

This application will expose a messaging endpoint that lets us exercise the TLS connectivity through RSocket. We just need a single controller to do the work:

ControllerMapping.kt
```kotlin
package example.rsocket.tls

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono

@Controller
class ControllerMapping {
    @MessageMapping("status")
    fun status() = Mono.just(true)
}
```

The RSocket messaging endpoint 'status' which return 'OK' will suffice our connectiity needs. Next, we need to TLS'ify the server.

### Spring Boot RSocket/TLS support

Let's add TLS to our RSocket Server by including some necessary paths and TLS related configuration options in `application.properties`:

application.properties
```properties
spring.application.name=rsocket-tls
spring.rsocket.server.port=9090
spring.rsocket.server.ssl.enabled=true              
spring.rsocket.server.ssl.client-auth=none           
spring.rsocket.server.ssl.protocol=TLS               
spring.rsocket.server.ssl.key-store=classpath:keystore/server.keystore.jks
spring.rsocket.server.ssl.key-store-type=JKS       
spring.rsocket.server.ssl.key-store-password=111111
spring.rsocket.server.ssl.trust-store=classpath:keystore/server.truststore.jks
spring.rsocket.server.ssl.trust-store-password=111111
```

So, this is the bulk of the TLS enabling code thats needed to secure the server with TLS. We specify that TLS v1.2 is used, where and what type our keystores are, and finally the password to access them.


### Securing the client

The client is a standard [RSocketRequester]() that uses TCP to transmit frames. We can lock down the TCP connection by creating a new instance of [TcpClientTransport]() and sending it to the Requester when we create it.  Lets take a look at client configuration for our TCPClientTransport below;

```kotlin
open class PKITransportFactory(private val trustFile: File,
                               private val keyFile: File,
                               private val jksPass: String) {

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
```

## Test Securely/Un-Securely

To test the validity of certificates and their positions in the keystore, we will write a couple of tests that
certify client's trust-store matches the key given from server's keystore.

First we will setup a test class:

```kotlin
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TlsRSocketApplicationTests {

}
```

We will need to create the requester, and it's TCP connection:

```kotlin
	@Autowired
	private lateinit var secureConnection: PKITransportFactory

	private lateinit var requester: RSocketRequester

	@BeforeAll
	fun setupOnce(@Value("\${spring.rsocket.server.port}") port: Int) {
		val securecon = secureConnection.tcpClientTransport("localhost", port)
		requester = RSocketRequester.builder().transport(securecon)

	}
```

Now, we can test the secure connection here:

```kotlin
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
```

Running the tests show we are able to hit the 'status' endpoint fine.

## Closing and Next Steps

This guide provided guidance on securing your RSocket servers through TLS security, the routines (scripting) involved, and what distinguishes different key types. Understanding that there are more options available is 'key' here. As your services scale, you may want to take advantage of [Spring Vault](https://cloud.spring.io/spring-cloud-vault/reference/html/), [CredHub](https://docs.cloudfoundry.org/credhub/) and a variety of options that help manage the provisioning of Key Certificates.

The next step in this topic will take advantage of [Spring Vault]() as a source of Key certificates. This should reduce the amount of time a developer spends in managing all the key types found in the server-scape.

## Information and Learning

[Standard Cipher Names](https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html)

[SSL Self-signing guide](https://knowledge.broadcom.com/external/article/166370/how-to-create-a-selfsigned-ssl-certifica.html)

[Another Process of Generating SSL](https://www.digicert.com/kb/ssl-support/openssl-quick-reference-guide.htm)

[Converting Certs to PKCS12](https://docs.vmware.com/en/VMware-Horizon-7/7.13/horizon-scenarios-ssl-certificates/GUID-17AD1631-E6D6-4853-8D9B-8E481BE2CC68.html)