package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
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

public class Client {
	private static final int BUFFER_SIZE = 4096;
	private static final Charset CHARSET = Charset.forName("UTF-8");

	public static void main(String[] args) {
		if (3 != args.length) {
			usage();
			return;
		}
		Client client;
		try {
			client = new Client(args[0], args[1], Integer.parseInt(args[2]));
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

	public void start() {
		if (isRunning.getAndSet(true)) {
			throw new IllegalStateException("Client already isRunning.");
		}
		thread.start();
	}

	private static void usage() {
		System.out.println("Usage: ClientJarRet <clientID> <serverAddress> <serverPort>\n");
	}

	private final Thread thread;
	private final String clientID;
	private final SocketChannel sc;
	private final InetSocketAddress serverAddress;
	private final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private TaskWorker taskWorker;
	private final Charset ascii = Charset.forName("ASCII");

	public Client(String clientID, String address, int port) throws IOException {

		serverAddress = new InetSocketAddress(address, port);
		this.clientID = clientID;
		sc = SocketChannel.open();

		Runnable runnable = () -> {
			try {
				sc.connect(serverAddress);
				System.out.println("Connected to server");
				while (!Thread.interrupted()) {
					try {
						while (taskWorker == null) {
							bb.put(ascii.encode(getTask()));
							bb.flip();
							System.out.println("Requesting new task...");
							sc.write(bb);
							bb.compact();
							try {
								HTTPReader reader = new HTTPReader(sc, bb);
								HTTPHeader header = reader.readHeader();
								taskWorker = newTaskWorker(header, reader);
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
						//int taskNumber = Integer.parseInt(taskWorker.getJobId());
						int taskNumber = Integer.parseInt(taskWorker.getTask());
						System.out.println("***"+taskWorker.getJobId()+" value of Task number : " + taskWorker.getTask());
						String result = null;
						try {
							result = worker.compute(taskNumber);
						} catch (Exception e) {
							// e.printStackTrace();
							setBufferError("Computation error");
							return;
						}
						if (null == result) {
							setBufferError("Computation error");
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
						setBufferAnswer(result);
						// return;

						// send to server with post
						System.err.println("Send result to server in mode POST ");
						bb.flip();
						sc.write(bb);
						bb.flip();

						System.out.println("Result sent");
						bb.clear();

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
						case 200: {
							System.out.println("Everything is Ok : HTP/1.1 200");
							break;
						}
						default:
							System.out.println("Error from server: " + code);
						}

					} catch (ClosedByInterruptException e) {
						// Close client with thread.interrupt();
						break;
					} catch (IOException e) {
						e.printStackTrace(System.err);
						break;
					} catch (Exception e) {
						e.printStackTrace(System.err);
					} finally {
						taskWorker = null;
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

	private TaskWorker newTaskWorker(HTTPHeader header, HTTPReader reader)
			throws IOException, IllegalStateException, NoTaskException {
		ByteBuffer bbIn = reader.readBytes(header.getContentLength());
		bbIn.flip();
		String response = header.getCharset().decode(bbIn).toString();
		System.out.println("result : " + response);
		ObjectMapper mapper = new ObjectMapper();

		// http://stackoverflow.com/questions/23469784/com-fasterxml-jackson-databind-exc-unrecognizedpropertyexception-unrecognized-f
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.setVisibilityChecker(
				VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));

		TaskWorker taskWorker;
		try {
			taskWorker = mapper.readValue(response, TaskWorker.class);
			System.out.println("Worker: " + taskWorker.getTask() + "    Job: " + taskWorker.getJobId());
		} catch (JsonMappingException e) {
			JsonFactory factory = new JsonFactory();
			JsonParser parser = factory.createParser(response);
			if (parser.nextValue() == null) {
				throw new IllegalStateException("Empty response");
			}
			throw new NoTaskException(parser.getIntValue());
		}
		if (!taskWorker.isValid()) {
			System.out.println(response);
			HashMap<String, String> map = mapper.readValue(response,
					new TypeReference<HashMap<String, String>>() {
					});
			System.out.println("**********************"+map.get("ComeBackInSeconds"));
			int comeBackIn = Integer.parseInt(map.get("ComeBackInSeconds"));
			throw new NoTaskException(comeBackIn * 1000);
		}
		return taskWorker;
	}

	private void setBufferError(String errorMessage) throws IOException {
		ByteBuffer resultBb = constructResponse("Error", errorMessage);
		addSendHeader(resultBb.position());
		resultBb.flip();
		bb.put(resultBb);
		System.out.println("Generating error: " + errorMessage);
	}

	private ByteBuffer constructResponse(String key, String msg) throws JsonProcessingException {
		System.out.println("-------------------------------------------------");
		HashMap<String, Object> map = taskWorker.buildMap();
		map.put("ClientId", clientID);
		map.put(key, msg);

		ObjectMapper mapper = new ObjectMapper();
		String response = mapper.writeValueAsString(map).replace("\\n","").replace("\\t", "").replace("\"{", "{").replace("}\"", "}")
				.replace("\\", "");

		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		System.err.println("job id : " + map.get("JobId"));
		System.err.println("task id : " + map.get("Task"));
		System.err.println("json value : " + response);
		bb.putLong(Long.parseLong((String) map.get("JobId")));
		bb.putInt(Integer.parseInt((String) map.get("Task")));
		bb.put(CHARSET.encode(response));
		return bb;
	}

	private void addSendHeader(int size) throws IOException {
		System.out.println("Size : " + size);
		Charset ascii = Charset.forName("utf-8");
		System.err.println("head : " + postAnswer(size));
		ByteBuffer header = ascii.encode(postAnswer(size));
		bb.put(header);

	}

	private void setBufferAnswer(String answer) throws IOException {
		try {
			System.out.println("*******----------************" + answer);
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

	private String postAnswer(int size) throws IOException {
		return "POST Answer HTTP/1.1\r\n" + "Host:" + sc.getRemoteAddress().toString() + "\r\n"
				+ "Content-Type: application/json\r\n" + "Content-Length: " + size + "\r\n" + "\r\n";
	}

	private String getTask() throws IOException {
		return "GET Task HTTP/1.1\r\nHost: " + sc.getRemoteAddress().toString() + "\r\n\r\n";
	}
}
