package com.chodavarapu.io;

import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class ReadWriteBufferTest {
    @Test
    public void basicReadWrite() throws Exception {
        ReadWriteBuffer buffer = new ReadWriteBuffer();

        OutputStreamWriter writer = new OutputStreamWriter(buffer.openOutputStream());
        writer.write("Test String\n");
        writer.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(buffer.openInputStream()));
        assertEquals("Test String", reader.readLine());

        writer.write("Another String\n");
        writer.flush();
        assertEquals("Another String", reader.readLine());
    }

    @Test(expected = BlockingReadTimeoutException.class)
    public void noDeadLockingForSingleReadWriteThread() throws Exception {
        ReadWriteBuffer buffer = new ReadWriteBuffer();

        OutputStreamWriter writer = new OutputStreamWriter(buffer.openOutputStream());
        writer.write("Test String\n");
        writer.flush();

        InputStream inputStream = buffer.openInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        assertEquals("Test String", reader.readLine());

        assertEquals(0, inputStream.read(new byte[1024]));

        writer.write("Another String\n");
        writer.flush();
        assertEquals("Another String", reader.readLine());
    }

    @Test
    public void noBlockingWhenOutputStreamsClosed() throws Exception {
        ReadWriteBuffer buffer = new ReadWriteBuffer();

        OutputStreamWriter writer = new OutputStreamWriter(buffer.openOutputStream());
        writer.write("Test String\n");
        writer.flush();

        InputStream inputStream = buffer.openInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        assertEquals("Test String", reader.readLine());

        new Thread(() -> {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        assertEquals(-1, inputStream.read(new byte[1024]));
    }
}
