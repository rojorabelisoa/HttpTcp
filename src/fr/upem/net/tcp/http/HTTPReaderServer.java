package fr.upem.net.tcp.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;

public class HTTPReaderServer extends HTTPReader {
	private final ByteBuffer buff;

	public HTTPReaderServer(SocketChannel sc, ByteBuffer buff) {
		super(sc, buff);
		this.buff = buff;
	}

	public HTTPReaderServer(SocketChannel sc, ByteBuffer buff, Charset charset) {
		super(sc, buff);
		this.buff = buff;
	}

	/**
	 * @return The ASCII string terminated by CRLF
	 *         <p>
	 *         The method assume that buff is in write mode and leave it in
	 *         write-mode The method never reads from the socket as long as the
	 *         buffer is not empty
	 * @throws HTTPException
	 * @throws IOException
	 *             HTTPException if the connection is closed before a line could
	 *             be read
	 */
	public String readLineCRLF() throws HTTPStateException {
		StringBuilder sb = new StringBuilder();
		boolean justReadCR = false;
		boolean finished = false;
		int position = buff.position();
		while (buff.hasRemaining() && !finished) {
			byte current = buff.get();
			if (current == LF && justReadCR) {
				finished = true;
			}
			justReadCR = (current == CR);
		}
		int end = buff.position();
		ByteBuffer tmp = buff.duplicate();
		tmp.position(position);
		tmp.limit(end);
		sb.append(charset.decode(tmp));
		if (!finished) {
			throw new HTTPStateException("Header is not complete.");
		}
		sb.delete(sb.length() - 2, sb.length());
		return sb.toString();

	}

	/**
	 * @param bb
	 * @return The HTTPHeader object corresponding to the header read
	 * @throws IOException
	 *             HTTPException if the connection is closed before a header
	 *             could be read if the header is ill-formed
	 */
	public HTTPHeader readHeader() throws HTTPStateException, HTTPException {
		buff.flip();
		String statusLine = readLineCRLF();
		HashMap<String, String> map = new HashMap<>();
		while (true) {
			String line = readLineCRLF();
			if (line.length() == 0) {
				break;
			}
			int index_of_separator = line.indexOf(":");
			String s;
			if (null != (s = map.putIfAbsent(
					line.substring(0, index_of_separator),
					line.substring(index_of_separator + 2)))) {
				s.concat("; " + line);
				map.put(statusLine, s);
			}
		}
		HTTPHeader header = HTTPHeader.create(statusLine, map);
		buff.compact();
		return header;
	}
}