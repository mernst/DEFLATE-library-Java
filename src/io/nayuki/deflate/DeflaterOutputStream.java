/* 
 * DEFLATE library (Java)
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/deflate-library-java
 * https://github.com/nayuki/DEFLATE-library-Java
 */

package io.nayuki.deflate;

import org.checkerframework.checker.index.qual.*;
import org.checkerframework.common.value.qual.MinLen;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * Compresses a byte stream into a DEFLATE data stream (raw format without zlib or gzip headers or footers).
 * <p>Currently only supports uncompressed blocks for simplicity, which actually
 * expands the data slightly, but still conforms to the DEFLATE format.</p>
 * <p>This class performs its own buffering, so it is unnecessary to wrap a
 * {@link BufferedOutputStream} around the {@link OutputStream} given to the constructor.</p>
 */
public final class DeflaterOutputStream extends FilterOutputStream {
	
	private byte @MinLen(6) [] buffer; // Index starts off as 5, so buffer has to be at lest of size 6
	private @IndexFor("this.buffer") int index; // The index should be a valid index within buffer range
	private boolean isFinished;


	public DeflaterOutputStream(OutputStream out) {
		super(out);
		buffer = new byte[5 + 65535];
		index = 5;
		isFinished = false;
	}

	
	@SuppressWarnings("cast") // The casting below is safe because if index becomes equal to the size of the input array
	// and we want to write another character, we check if index is equal to the length of buffer and reset it if so
	// by calling writeBuffer() method. It is safe to assume that each time index is incremented by one in this method,
	// it would remain within buffer bounds.
	public void write(int b) throws IOException {
		if (isFinished)
			throw new IllegalStateException();
		if (index == buffer.length)
			writeBuffer(false);
		buffer[index] = (byte) b;
		index = (@IndexFor("this.buffer") int) (index + 1); // Explained above why this is safe
	}
	
	@SuppressWarnings("cast") // There are 2 castings in this method:
	// The first one is redundant, because Math.min takes as arguments two certainly not negative arguments, so the
	// result can't be negative. We made sure len is non-negative and index is always smaller than length of buffer
	// The second cast is because of a false positive of the checker. The method System.arrayCopy provided useful
	// documentation that helped me calculate the correct offsets for b and buffer arrays, such that chunk variable
	// always stays within correct bounds.
	public void write(byte[] b, @IndexOrHigh("#1") int off, @IndexOrHigh("#1") int len) throws IOException {
		// The offset and the length have to be within the bounds of array b, and can be equal to the size of the array
		// because the exception is thrown only when they exceed this limit. Otherwise, the method works as expected.
		if (isFinished)
			throw new IllegalStateException();
		if (off < 0 || off > b.length || len < 0 || b.length - off < len)
			throw new IndexOutOfBoundsException();
		while (len > 0) {
			if (index == buffer.length)
				writeBuffer(false);
			@NonNegative @LTLengthOf(value = {"b","buffer"}, offset = {"off - 1", "this.index - 1"}) int chunk =
					(@NonNegative int) Math.min(len, buffer.length - index);
			// Below there is a method call for arrayCopy that takes chunk as an argument for length. I used the
			// documentation of the method to see how to provide a correct chunk so that the method doesn't throw
			// an exception. I used the offset parameter of @LTLengthOf annotation to make sure chunk is always passed
			// correctly to the function and, when we add chunk to index below, the index stays within bounds
			System.arraycopy(b, off, buffer, index, chunk);
			off += chunk;
			len -= chunk;
			index = (@IndexFor("buffer") int) (index + chunk); // If we add chunk to index it stays within buffer bounds
			// as chunk was correctly verified before. This casting is redundant, but the checker issued a warning.
		}
	}
	
	
	public void flush() throws IOException {
		if (isFinished)
			throw new IllegalStateException();
		if (index > 5)  // If current block is not empty
			writeBuffer(false);
		out.flush();
	}
	
	
	public void finish() throws IOException {
		if (isFinished)
			throw new IllegalStateException();
		writeBuffer(true);
		isFinished = true;
	}
	
	
	public void close() throws IOException {
		if (!isFinished)
			finish();
		out.close();
	}
	
	@SuppressWarnings("cast") // Obviously, index is never lower than 5. When we reset it, it becomes 5. We never
	//subtract anything from it so it gets lower. Therefore, we can safely cast index - 5 to a non-negative integer.
	private void writeBuffer(boolean isFinal) throws IOException {
		if (isFinished)
			throw new IllegalStateException();

		// Fill in header fields
		@NonNegative int len = (@NonNegative int) (index - 5); // The length should be non-negative
		int nlen = len ^ 0xFFFF;
		buffer[0] = (byte)(isFinal ? 0x01 : 0x00);
		buffer[1] = (byte)(len >>> 0);
		buffer[2] = (byte)(len >>> 8);
		buffer[3] = (byte)(nlen >>> 0);
		buffer[4] = (byte)(nlen >>> 8);
		
		// Write and reset
		out.write(buffer, 0, index);
		index = 5;
	}
	
}
