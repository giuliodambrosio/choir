# choir
A java application to broadcast music in peer to peer fashion based on multicast udp packets.

Once compiled, launch
Choir is a social audio files player. 
Every connected client shares its own audio files.
When an audio file is over a new client is picked randomly and an audio file is randomly chosen among the files that the client is sharing.
All the connected clients play the same content at the same time. 

Once compiled, launch like this:
java -classpath choir.jar uk.co.dambrosio.choir.Choir <audio-files-path> [multicast-group-address] [multicast-group-port]
