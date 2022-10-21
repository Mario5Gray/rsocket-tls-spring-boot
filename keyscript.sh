#!/bin/sh

#Generate Keys and keystores

#Generate CA key, cert
openssl genrsa -out ca.key 2048 -nodes -sha256
openssl req -x509 -new -nodes -key ca.key -passout pass:111111 -out ca.cer -days 365 -sha256 -subj "/CN=CARoot"

#Generate server key, CSR, sign CSR w/ CA
openssl genrsa -out server.key 2048
openssl req -new -key server.key -sha256 -out server.csr -subj "/CN=localhost,OU=Unknown,O=Unknown,L=Unknown,ST=Unknown,C=Unknown"
openssl x509 -req -CA ca.cer -CAkey ca.key -in server.csr -out server.pem -days 3650 -CAcreateserial -sha256 -passin pass:111111

# Import Root cert, Server cert to pkcs12 keystore for the server
cat ca.cer server.pem > serverca.pem
openssl pkcs12 -export -in serverca.pem -inkey server.key -name localhost -password pass:111111 > server-ks.p12

# Import Root cert into Server's trust-store
openssl pkcs12 -export -in ca.cer -inkey ca.key -name caroot -password pass:111111 > server-ts.p12

#Generate client key, CSR, sign CSR w/ CA
openssl genrsa -out client.key 2048 -nodes
openssl req -new -key client.key -sha256 -out client.csr -subj "/CN=clientuser,OU=Unknown,O=Unknown,L=Unknown,ST=Unknown,C=Unknown"
openssl x509 -req -CA ca.cer -CAkey ca.key -in client.csr -out client.pem -days 3650 -CAcreateserial -sha256 -passin pass:111111

# Import Root cert, Client cert to pkcs12 keystore for the client
cat ca.cer client.pem > clientca.pem
openssl pkcs12 -export -in clientca.pem -inkey client.key -name localclient -password pass:111111 > client-ks.p12

# import Root cert into client's trust-store
openssl pkcs12 -export -in ca.cer -inkey ca.key -name caroot -password pass:111111 > client-ts.p12

#Import client cert to pkcs12 keystore of the client
# Import Server.cer to Server
#keytool -import -keystore server-ts.jks -storetype JKS -storepass 111111 -keypass 111111 -noprompt -alias localhost -file server.pem

# Import Key Pair into JKS (https://docs.oracle.com/en/database/other-databases/nosql-database/21.2/security/import-key-pair-java-keystore.html)
keytool -importkeystore -srckeystore server-kp.p12 -destkeystore server-ts.jks -srcstoretype pkcs12 -alias localhost -srcstorepass 111111 -deststorepass 111111

# Import CA Root to Client
keytool -import -keystore client-ts.jks -storetype JKS -storepass 111111 -keypass 111111 -noprompt -alias CARoot -file ca.cer

# Import Server.cer to Client
#keytool -import -keystore client-ts.jks -storetype JKS -storepass 111111 -keypass 111111 -noprompt -alias client -file client.pem

# Import Key Pair into JKS (https://docs.oracle.com/en/database/other-databases/nosql-database/21.2/security/import-key-pair-java-keystore.html)
openssl pkcs12 -export -in client.pem -inkey client.key -name client > client.p12
keytool -importkeystore -srckeystore client.p12 -destkeystore client-ts.jks -srcstoretype pkcs12 -alias client -srcstorepass 111111 -deststorepass 111111

# VIEW keystore 
keytool -list -keystore server-ts.jks -storetype JKS -storepass 111111 -keypass 111111
keytool -list -keystore client-ts.jks -storetype JKS -storepass 111111 -keypass 111111

# Create PKCS12 copy of the JKS keystore
keytool -importkeystore -srckeystore server-ts.jks -destkeystore server-ts.p12 -srcstoretype JKS -deststoretype PKCS12 -deststorepass 111111 -srcstorepass 111111
keytool -importkeystore -srckeystore client-ts.jks -destkeystore client-ts.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass 111111 -deststorepass 111111

# VIEW P12
#openssl pkcs12 -nokeys -info -in server-ts.P12 -passin pass:111111