# Spring RSocket Security with TLS

This guide will help you configure TLS with your new or existing RSocket services using Spring Boot 2.7.x. We will review a few options for generating the cerficate, as well as importing and utilizing that certificate in your Spring Boot application.

It is assumed the developer knows about Kotlin or at least JAVA 11 and uses Spring Boot. Dont worry if you're new to RSocket for Spring Boot or Reactive streams. All of the TLS security concerns relate to the Transport, not the protocol itself. Of course, the best place to understand are [the reference docs](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#appendix.application-properties.rsocket), so please read them!

## Background on the PKI

SSL/TLS is a type of Public Key Infrastructure (PKI). Therefore PKI has the concept of a private and publy key pair. Writers of an encrypted message will use the private key to generate the encrypted bits. While readers (non trusted parties) can use the public portion to decrypt the message. Writers keep the private key private, while distributing public keys to readers.

### Keystore vs Truststore

One thing to note is the terms 'keystore', 'truststore' seem to get conflated and infer different things depending on context. Lets start with a basic description of a keystore's relation to overall SSL/TLS certificate handling.  

A Keystore is a repostiory containing one or more Certificates and Crytographic keys. Usually used to contain a private key and the accompanying X.509 certificate of a server. A server uses the key in the keystore to generate encrypted messages and that identifies it's self along the chain of trust. Those encrypted messages must be trusted downstream, which brings us to the truststore.

We can turn our self-signed certificate into a `PKCS12` formatted keystore:
```bash
openssl pkcs12 -export -out server.p12 -inkey self.key -in self.crt -certfile self.crt
```

A Truststore contains certificates from various Certified Authorities that verify the certificate presented by a server in an SSL connection. For example: should we have signed our Certificate by a known CA, our certificate would reflect this (signing by CA's private key) and our program would call out to our truststore to validate the identity of that signing CA.

We can view the JKS formatted Java truststore with [Keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html):
```bash
keytool -list -keystore $(/usr/libexec/java_home)/lib/security/cacerts
```

The output is all of the trusted CA certificate fingerprints. This allows Java to communicate with Internet PKI such as SSL via HTTP. Now, in the next sections, we move onto the topic of standing up an encrypted channel RSocket server and client!

## Generating the Certificates

What is the procedure in generating a certificate? There are many steps here. First we need a Private Key, then we need to create a Certificate Signing Request (CSR) using that Private Key. Finally a third party Certificate Authority (CA) else, or our own enterprise will convert that CSR into a Certificate. This certificate should stay on the host that needs it. It should not get distributed to anyone else since at the logistics of managing a single self-signed key (that is untrusted by 3rd parties) is quite complex. 

Alternatively we will create the private key, then generate a CA ROOT certificate to establish the chain-of-trust. This ROOT certificate gets distributed to all devices and software that will eventually handle a key signed by it; it's like we're our own enterprise signing keys. The process for each individual certificate uses our own CA Root to sign the CSR's. This way as single applications come and go, there is no need to re-distribute keys. 

In this guide we will explore both certificate creation options, then utilize our own CA Root to implement the TLS RSocket application.

### Generating the Private Key

A private key will usually get stored in [PEM]() format with the bits representing a the writer's crypto-algorithm choice such as DES, RSA2048, Blowfish, etc... Without going into too much detail, the private key _always_ stays with the creating entity. Since the Public Key is derived from the Private Key, our output private key file will contain the private/public key pair.

Lets generate a key pair:
```bash
openssl genrsa -out self.key 2048
```

This tells openssl to generate a new RSA key 2048 bits long and store it in a file `self.key`. The resulting file is Base64 encoded.

You can even check and inspect the key:
```
openssl rsa -in self.key -text -check
```

The output is somewhat lengthy and includes a status indicator of the key's validity, the HEXADECIMAL representation of the numeric key, and it's Base64 encoding.

### Certificate Signing Request

We will need to use that key in a certificate. Thus the [CSR](https://en.wikipedia.org/wiki/Certificate_signing_request) is a file containing the public key and some organizational metadata used to identify the originating entity. A signitory party (the CA) will use it's private key to sign your CSR upon success and an SSL certificate is the end-result of this CSR procedure.

The CSR will contain information about the requesting entity including:

* Private Key
* FQDN - Fully Qualified Domain Name
* DN Owner - Distinguished Name of the owner entity
* DN Signer - Distinguished Name of the issuer entity
* Email
* Expiry - Time of expiry for the certificate

Create the Signing Request using our private key. The output is 'self.csr' containing the Base64 encoded [X.509](https://en.wikipedia.org/wiki/X.509) Certificate Request:
```bash
openssl req -new -key self.key -sha256 -out self.csr -subj "CN=localhost,OU=Unknown,O=Unknown,L=Unknown,ST=Unknown,C=Unknown"
```

Now, we need to get this CSR over to an Enterprise Certificate Authority or external CA (e.g. Verisign) for signing. This will ensure other parties see that a trusted 3rd party has guaranteed our public key. Alternatively, we will self-sign the CSR and produce our own Certificate.

### Signing Option 1: Self-Sign

The purpose of a certificates is to validate a public key is owned by the creating entity of the key without giving up the private key! This process occurs through a trusted CA (like Verisign) or an internal Enterprise CA, however for this example we will self-sign.

The output is our self-signed SSL Certificate in 'self.crt':
```bash
openssl x509 -req -days 365 -in self.csr -signkey self.key -sha256 -out self.crt
```

Now, we need to get this certificate somewhere and in a format that our server can understand. We have a couple options for that format: [JKS](https://vdc-download.vmware.com/vmwb-repository/dcr-public/93c0444e-a6cb-46a0-be25-b27a20f8c551/ac6ea73b-569b-4fff-80f1-e4144f541ac8/GUID-7FB0CDA2-BE63-49A4-B76C-BB806C3194AC.html) which is a JAVA specification for storing certificates. Then there is [PKCS12](https://en.wikipedia.org/wiki/PKCS_12) which is the platform agnostic format.

### Signing Option 2: Our own CA Root to the rescue

This is a little more complex because instead of a self-signed Certificate, we will instead create a trusted CA of our own then sign certificates with it. The Root CA will be distributed to any client/servers/browsers/devices, while the individual certificates will get managed on the hosts that they belong to. This way the chain-of-trust is established with the RootCA, and we dont need to re-deploy a certificate for each known use-case.

To do this, we can take our own Private key and generate a Root Certificate using OpenSSL:

```bash
openssl req -x509 -new -key self.key -passout pass:111111 -out ca.cer -days 365 -sha256 -subj "/CN=CARoot" -nodes
```

Then, we can install this Root Certificate to the devices/service keystores needed. In this case, we can install the Root Certificate to the keystore that our application will use:
```bash
keytool -import -keystore server-ts.jks -storetype JKS -storepass 111111 -keypass 111111 -noprompt -alias CARoot -file ca.cer
```

## Setup the RSocket Server

The server is a simple RSocket process. It only needs to respond to a message endpoint, and be encrypted along the way. 

## Add TLS

## Test Securely/Un-Securely

## Closing and Next Steps

This guide provided guidance on securing your RSocket servers through TLS security, the routines (scripting) involved, and what distinguishes different key types. Understanding that there are more options available is 'key' here. As your services scale, you may want to take advantage of [Spring Vault](https://cloud.spring.io/spring-cloud-vault/reference/html/), [CredHub](https://docs.cloudfoundry.org/credhub/) and a variety of options that help manage the provisioning of Key Certificates.

The next step in this topic will take advantage of [Spring Vault]() as a source of Key certificates. This should reduce the amount of time a developer spends in managing all the key types found in the server-scape.

## Information and Learning

[SSL Self-signing guide](https://knowledge.broadcom.com/external/article/166370/how-to-create-a-selfsigned-ssl-certifica.html)

[Another Process of Generating SSL](https://www.digicert.com/kb/ssl-support/openssl-quick-reference-guide.htm)

[Converting Certs to PKCS12](https://docs.vmware.com/en/VMware-Horizon-7/7.13/horizon-scenarios-ssl-certificates/GUID-17AD1631-E6D6-4853-8D9B-8E481BE2CC68.html)