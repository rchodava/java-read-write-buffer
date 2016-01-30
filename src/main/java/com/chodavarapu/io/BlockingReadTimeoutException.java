package com.chodavarapu.io;

import java.io.IOException;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class BlockingReadTimeoutException extends IOException {
    public BlockingReadTimeoutException(String message) {
        super(message);
    }
}
