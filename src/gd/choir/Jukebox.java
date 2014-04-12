/**
 * 
 */
package gd.choir;

import java.io.IOException;
import java.net.UnknownHostException;

import gd.choir.client.Client;
import gd.choir.server.Server;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

/**
 * @author Giulio D'Ambrosio
 */
public class Jukebox {
	private static final String	JB_MULTICAST_ADDR="239.255.49.1";
	private static final char	JB_MULTICAST_PORT=9871;
	private static final String	JB_MULTICAST_AUDIO_PATH="./audiosamples/";
	private static final char	JB_SERVER_PORT=9872;
	/**
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		Server server;
		Client client;
		String	groupAddress=JB_MULTICAST_ADDR;
		char groupPort=JB_MULTICAST_PORT;
		String	audioPath=JB_MULTICAST_AUDIO_PATH;

        if (args.length == 0) {
            CommandUsagePrinter usage = new CommandUsagePrinter();
            usage.display();
            System.exit(-1);
        }

        CommandParameterParser commandParameterParser = new CommandParameterParser(args, groupAddress, groupPort, audioPath).invoke();
        groupAddress = commandParameterParser.getGroupAddress();
        groupPort = commandParameterParser.getGroupPort();
        audioPath = commandParameterParser.getAudioPath();

        while (true) {
			server=null;
			try {
				client=new Client(groupAddress, groupPort, audioPath);
				if (!client.connect()) {
					System.err.println("No active server found: becoming a server");
					server=new Server(groupAddress,groupPort,JB_SERVER_PORT,client);
					server.start();
				}
				int numr=0;
				while (true){
					if (client.isConnected()){
						client.start();
						break;
					}
					if (numr++>2) {
						System.err.println("Could not establish a connection with the server: bailing out");
						client.stop();
						break;
					}
					System.err.println("Looking for a server");
					Thread.sleep(1000);
				}

                // Waiting for the threads to end.
				if (server!=null && server.getRunningThread()!=null) {
					server.getRunningThread().join();
				}
				if (client.getRunningThread()!=null) {
					client.getRunningThread().join();
				}
					
	
			} catch (UnknownHostException e) {
				System.out.println("Can't connect to: "+groupAddress+": "+e.getMessage());
				System.exit(-1);
			} catch (IOException e) {
				System.out.println("Can't connect to: " + groupAddress + ": " + e.getMessage());
				System.exit(-1);
			} catch (InterruptedException e) {
				System.exit(-1);
			} catch (Exception e) {
				System.out.println("Error: "+e.getMessage());
				System.exit(-1);
			}
		}
	}

    private static class CommandUsagePrinter {

        private void display() {
            String supportedAudioFiles = getSupportedAudioFileTypes();

            System.out.println(
                    "Choir is a social audio files player. \n" +
                    "Every connected client shares its own audio files. " +
                    "When an audio file is over a new client is picked randomly and an audio file " +
                    "is randomly chosen among the files that the client is sharing." +
                    "All the connected clients play the same content at the same time. \n" +
                    "The currently supported audio formats are: " +
                    supportedAudioFiles + "\n\n" +
                    "Usage: choir <audio-files-path> [multicast-group-address] [multicast-group-port]"
            );
        }

        private String getSupportedAudioFileTypes()
        {
            AudioFileFormat.Type[] types = AudioSystem.getAudioFileTypes();
            String result = "";
            for (AudioFileFormat.Type type : types) {
                result += (result.length() > 0 ? ", " : "") + type.toString();
            }

            return result + ".";
        }

    }

    private static class CommandParameterParser {
        private String[] args;
        private String groupAddress;
        private char groupPort;
        private String audioPath;

        public CommandParameterParser(String[] args, String groupAddress, char groupPort, String audioPath) {
            this.args = args;
            this.groupAddress = groupAddress;
            this.groupPort = groupPort;
            this.audioPath = audioPath;
        }

        public String getGroupAddress() {
            return groupAddress;
        }

        public char getGroupPort() {
            return groupPort;
        }

        public String getAudioPath() {
            return audioPath;
        }

        public CommandParameterParser invoke() {
            for (int i=0;i<args.length;i++) {
                switch (i) {
                case 0:
                    audioPath=args[i];
                    break;
                case 1:
                    groupAddress=args[i];
                    break;
                case 2:
                    groupPort=(char) Integer.parseInt(args[i]);
                    break;
                default:
                    System.err.println("Usage: choir <audio-files-path> [multicast-group-address] [multicast-group-port]");
                    System.exit(-1);
                }
            }
            return this;
        }
    }
}
