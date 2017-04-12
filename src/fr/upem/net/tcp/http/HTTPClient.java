package fr.upem.net.tcp.http;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

import upem.jarret.task.NoTaskException;
import upem.jarret.task.TaskWorker;
import upem.jarret.worker.Worker;

public class HTTPClient {
	private static final int BUFFER_SIZE = 4096;
	private static final Charset CHARSET = Charset.forName("UTF-8");

	public static void main(String[] args) {
		if (3 != args.length) {
			usage();
			return;
		}
		HTTPClient client;
		try {
			client = new HTTPClient(args[0], args[1], Integer.parseInt(args[2]));
		} catch (NumberFormatException e) {
			usage();
			return;
		} catch (IOException e) {
			e.printStackTrace(System.err);
			return;
		}
		client.start();
		// TODO scan System.in for shutdown in order to terminate client.
	}

	/**
	 * Display usage in <i>System.out</i>
	 */
	public static void usage() {
		System.out.println("Usage: ClientJarRet <clientID> <serverAddress> <serverPort>\n");;
	}

	

	private final Thread thread;
	private final String clientID;
	private final SocketChannel sc;
	private final InetSocketAddress serverAddress;
	private final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	private TaskWorker taskWorker;

	public HTTPClient(String clientID, String address, int port) throws IOException {

		serverAddress = new InetSocketAddress(address, port);
		this.clientID = clientID;
		sc = SocketChannel.open();

		Runnable runnable = () -> {
			try {
				sc.connect(serverAddress);
				System.out.println("Connected to server");
				while (!Thread.interrupted()) {
					try {
						sendAndGetTask(); // send request to server with get
						sendAnswer();// send to server with post
						getAnswerAndReset();
					} catch (ClosedByInterruptException e) {
						// Close client with thread.interrupt();
						break;
					} catch (IOException e) {
						e.printStackTrace(System.err);
						break;
					} catch (Exception e) {
						e.printStackTrace(System.err);
					} finally {
						endTask();
					}
				}
			} catch (IOException e) {
				e.printStackTrace(System.err);
			} finally {
				isRunning.set(false);
				System.out.println("Client terminated.");
			}
		};
		thread = new Thread(runnable);
	}

	/**
	 * Start the client.
	 */
	public void start() {
		if (isRunning.getAndSet(true)) {
			throw new IllegalStateException("Client already isRunning.");
		}
		thread.start();
	}

	/**
	 * Stop the client.
	 */
	public void shutdown() {
		System.out.println("Shutting down...");
		thread.interrupt();
	}

	/**
	 * Check if client is isRunning.
	 * 
	 * @return True if client is isRunning.
	 */
	public boolean isrunning() {
		return isRunning.get();
	}

	/**
	 * Send result to server.
	 * 
	 * @throws IOException
	 *             - If some I/O error occurs.
	 */
	private void sendAnswer() throws IOException {
		bb.flip();
		sc.write(bb);
		System.err.println("Send result to server in mode POST ");
		
		System.out.println("Result sent");
		bb.clear();
	}

	/**
	 * Read final response from server then reset job's data.
	 * 
	 * @throws IOException
	 *             - If some I/O error occurs.
	 */
	private void getAnswerAndReset() throws IOException {
		// Answer sent
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header;
		try {
			header = reader.readHeader();
		} catch (HTTPException e) {
			System.out.println("Cannot read header");
			return;
		}
		int code = header.getCode();
		switch (code) {
		case 200:{
			System.out.println("$$$$$$$$$$$$$ok");
			break;
			}
		default:
			System.out.println("Error from server: " + code);
		}
	}

	/**
	 * Get task from server, execute it then fill ByteBuffer.
	 * 
	 * @throws IOException
	 *             - If some I/O error occurs.
	 */
	private void sendAndGetTask() throws IOException, NoTaskException {
		while (taskWorker == null) {
			// No taskWorker yet
			requestNewTask();
			try {
				getRequestedTask();
			} catch (IllegalStateException e) {
				// Content is not fully received
				return;
			}
		}
		Worker worker;
		try {
			worker = taskWorker.getWorker();
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			// setBufferError(e.getMessage());
			// This error should not be reported to the server.
			throw new IOException("Invalid jar file.");
		}
		int taskNumber = Integer.parseInt(taskWorker.getJobId());
		System.out.println("*** value of Task number : "+taskNumber);
		String result = null;
		try {
			result = worker.compute(taskNumber);
		} catch (Exception e) {
			setBufferError("Computation error ***exception*** ");
			return;
		}
		if (null == result) {
			setBufferError("Computation error ***result is null***");
			return;
		}
		HashMap<String, String> map = new HashMap<String, String>();
		ObjectMapper mapper = new ObjectMapper();
		try {
			map = mapper.readValue(result, new TypeReference<HashMap<String, Object>>() {
			});
		} catch (JsonParseException e) {
			setBufferError("Answer is not valid JSON");
			return;
		} catch (JsonMappingException e) {
			setBufferError("Answer is nested");
			return;
		}
		/*StringBuilder sb = new StringBuilder();
		sb.append("{"); 
		for(Entry<String, String> keyMapping : map.entrySet()){
			sb.append(' ');
			sb.append('"');
			String[] token= keyMapping.toString().split("=",2);
			sb.append(token[0]);
			sb.append('"');
			sb.append(" : ");
			sb.append(token[1]);
			sb.append(",");
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append("}");
		setBufferAnswer(sb.toString());*/
		setBufferAnswer(result);
		return;
	}

	/**
	 * Fill taskWorker field with response in ByteBuffer.
	 * 
	 * @throws NoTaskException
	 * @throws IllegalStateException
	 * @throws IOException
	 *             - If some I/O error occurs.
	 */
	private void getRequestedTask() throws IllegalStateException, IOException, NoTaskException {
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header = reader.readHeader();
		taskWorker = newTaskWorker(header, reader);
	}

	/**
	 * Fill ByteBuffer with a error message, ready to send to server.
	 * 
	 * @param errorMessage
	 *            - Message to send.
	 * @throws IOException
	 *             - If some I/O error occurs.
	 */
	private void setBufferError(String errorMessage) throws IOException {
		ByteBuffer resultBb = constructResponse("Error", errorMessage);
		addSendHeader(resultBb.position());
		resultBb.flip();
		bb.put(resultBb);
		System.out.println("Generating error: " + errorMessage);
	}

	/**
	 * Fill ByteBuffer with a result message, ready to send to server.
	 * 
	 * @param answer
	 *            - Result to send.
	 * @throws IOException
	 *             - If some I/O error occurs.
	 */
	private void setBufferAnswer(String answer) throws IOException {
		try {
			ByteBuffer resultBb = constructResponse("Answer", answer);
			addSendHeader(resultBb.position());
			resultBb.flip();
			bb.put(resultBb);
			System.out.println("Generating result: " + answer);
		} catch (BufferOverflowException e) {
			bb.clear();
			setBufferError("Too Long");
		}
	}

	/**
	 * Construct a response for the server.
	 * 
	 * @param key
	 *            - <i>Error</i>, <i>Answer</i>, ... This is the context of msg.
	 * @param msg
	 *            - Message to send.
	 * @return Built response.
	 * @throws JsonProcessingException
	 *             - If msg is not correct for the JSON mapper.
	 */
	
	private ByteBuffer constructResponse(String key, String msg) throws JsonProcessingException {
		System.out.println("-------------------------------------------------");
		Map<String, Object> map = taskWorker.buildMap();
		map.put("ClientId", clientID);
		map.put(key, msg);
		System.err.println("response id : "+msg);
		return getEncodedResponse(map);
	}

	/**
	 * Encode response in a new ByteBuffer.
	 * 
	 * @param map
	 *            - Datas to encode.
	 * @return Encoded response.
	 * @throws JsonProcessingException
	 *             - If map is not correct for the JSON mapper.
	 */
	private ByteBuffer getEncodedResponse(Map<String, Object> map) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		ByteBuffer bb = CHARSET.encode(mapper.writeValueAsString(map));
		bb.compact();
		return bb;
	}

	/**
	 * 
	 * @param size
	 * @throws IOException
	 */
	private void addSendHeader(int size) throws IOException {
		System.out.println("*******size : "+size);
		/*Map<String, String> fields = new HashMap<>();
		fields.put("Host", sc.getRemoteAddress().toString());
		fields.put("Content-Type", "application/json; charset=" + CHARSET.name());
		fields.put("Content-Length", size + "");
		HTTPHeader header = HTTPHeader.create("POST Answer HTTP/1.1", fields);
		bb.put(header.toBytes());*/
		Charset ascii = Charset.forName("ASCII");
		ByteBuffer header = ascii.encode("POST Answer HTTP/1.1\r\n"+
		"Host:"+sc.getRemoteAddress().toString()+
		"Content-Type: application/json\r\n"+
		"Content-Length: "+size+"\r\n"+
		"\r\n");
		bb.put(header);
		
	}

	/**
	 * Call this once all work is done.
	 */
	private void endTask() {
		taskWorker = null;
	}

	/**
	 * Send a request to get a new taskWorker.
	 * 
	 * @param sc
	 *            - Server channel.
	 * @throws IOException
	 */
	private void requestNewTask() throws IOException {
		/*
		 * Map<String, String> fields = new HashMap<>(); fields.put("Host",
		 * sc.getRemoteAddress().toString()); HTTPHeader header =
		 * HTTPHeader.create("GET Task HTTP/1.1", fields);
		 * 
		 * bb.put(header.toBytes());
		 */

		Charset ascii = Charset.forName("ASCII");
		bb.put(ascii.encode("GET Task HTTP/1.1\r\nHost: " + sc.getRemoteAddress().toString() + "\r\n\r\n"));
		bb.flip();
		System.out.println("Requesting new task...");
		sc.write(bb);
		bb.compact();
	}

	/**
	 * Get a taskWorker from server.
	 * 
	 * @param header
	 * 
	 * @param channel
	 * 
	 * @return new TaskWorker
	 * @throws IOException
	 * @throws NoTaskException
	 */
	private TaskWorker newTaskWorker(HTTPHeader header, HTTPReader reader)
			throws IOException, IllegalStateException, NoTaskException {
		ByteBuffer bbIn = reader.readBytes(header.getContentLength());
		bbIn.flip();
		String response = header.getCharset().decode(bbIn).toString();
		ObjectMapper mapper = new ObjectMapper();

		// http://stackoverflow.com/questions/23469784/com-fasterxml-jackson-databind-exc-unrecognizedpropertyexception-unrecognized-f
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.setVisibilityChecker(
				VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));

		TaskWorker taskWorker;
		try {
			taskWorker = mapper.readValue(response, TaskWorker.class);
			//System.out.println("Worker: " + taskWorker.getJobTaskNumber() + "    Job: " + taskWorker.getJobId());
		} catch (JsonMappingException e) {
			JsonFactory factory = new JsonFactory();
			JsonParser parser = factory.createParser(response);
			if (parser.nextValue() == null) {
				throw new IllegalStateException("Empty response");
			}
			throw new NoTaskException(parser.getIntValue());
		}
		if (!taskWorker.isValid()) {
			System.out.println("****** JSON RESPONSE="+response);
			//HashMap<String, Integer> map = mapper.readValue(response, new TypeReference<HashMap<String, Integer>>() {
			//});
			//int comeBackIn = map.get("ComeBackInSeconds");
			//throw new NoTaskException(comeBackIn * 1000);
		}
		return taskWorker;
	}

}
