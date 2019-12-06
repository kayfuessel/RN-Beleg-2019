javac ./src/Server.java
javac ./src/Client.java
rm ./bin/Server.class
rm ./bin/Client.class
mv ./src/Server.class ./bin/
mv ./src/Client.class ./bin/
chmod +x server-udp
chmod +x client-udp
