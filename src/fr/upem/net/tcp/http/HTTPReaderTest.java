package fr.upem.net.tcp.http;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 * <p>
 * Tests suit for the class HTTPReader
 */
public class HTTPReaderTest {

    /**
     * Test for ReadLineLFCR with a null Socket
     */

    @Test
    public void testReadLineLFCR1() throws IOException {
        try {
            final String BUFFER_INITIAL_CONTENT = "Debut\rSuite\n\rFin\n\r\nANEPASTOUCHER";
            ByteBuffer buff = ByteBuffer.wrap(BUFFER_INITIAL_CONTENT.getBytes("ASCII")).compact();
            HTTPReader reader = new HTTPReader(null, buff);
            assertEquals("Expected first line of the buffer", "Debut\rSuite\n\rFin\n", reader.readLineCRLF());
            ByteBuffer buffFinal = ByteBuffer.wrap("ANEPASTOUCHER".getBytes("ASCII")).compact();
            assertEquals("Expected buffer state",buffFinal.flip(), buff.flip());
        } catch (NullPointerException e) {
            fail("The socket must not be read until buff is entirely consumed.");
        }
    }

    /**
     * Test for ReadLineLFCR with a fake server
     * @throws java.io.IOException
     */
    @Test
    public void testLineReaderLFCR2() throws IOException {
        FakeHTTPServer server = new FakeHTTPServer("Line1\r\nLine2\nLine2cont\r\n",7);
        server.serve();
        SocketChannel sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("localhost", server.getPort()));
        HTTPReader reader = new HTTPReader(sc, ByteBuffer.allocate(12));
        assertEquals("Line1", reader.readLineCRLF());
        assertEquals("Line2\nLine2cont", reader.readLineCRLF());
        server.shutdown();
    }


    /**
     * Test for ReadLineLFCR with a fake server closing the connection before the line is fully read
     * We expect an HTTPException as the server close the connection before sending a complete LFCR terminated
     * line.
     * @throws java.io.IOException
     */
    @Test(expected = HTTPException.class)
    public void testLineReaderLFCR3() throws IOException {
            FakeHTTPServer server = new FakeHTTPServer("Line1\nLine2\nLine2cont\r",7);
            server.serve();
            SocketChannel sc = SocketChannel.open();
            sc.connect(new InetSocketAddress("localhost", server.getPort()));
            HTTPReader reader = new HTTPReader(sc, ByteBuffer.allocate(12));
            reader.readLineCRLF();
            server.shutdown();
        }



}