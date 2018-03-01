/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.nio;

/**
 * An attachment to a buffer.  Buffer attachments are strongly referenced by the
 * buffers they are attached to, but are not otherwise referenced.
 * <p>
 * A buffer attachment has an opportunity to replicate itself when a buffer is
 * duplicated or sliced or otherwise copied.  The circumstances of the copy are
 * given to the {@link #duplicate(Buffer, int, int, boolean, Class) duplicate()}
 * method.
 *
 * @since 11
 */
public abstract class BufferAttachment {
    /**
     * Construct a new instance.
     */
    protected BufferAttachment() {
    }

    /**
     * Duplicate the attachment for a duplicate buffer.  If this method returns
     * {@code null}, the duplicate will have no attachment.  The duplicate
     * buffer may have a different type than the original.
     * <p>
     * If this method throws a run-time exception, the exception will propagate
     * to the caller and the buffer will not be duplicated.
     *
     * @param original the original buffer object
     * @param offset the offset of the duplicate, in the range
     *      {@code 0 <= n <= original.capacity()}
     * @param length the length of the duplicate, in the range
     *      {@code 0 <= n <= original.capacity() - offset}
     * @param readOnly {@code true} if the returned buffer is read-only,
     *      {@code false} otherwise
     * @param duplicateClazz the type of buffer being returned
     * @return the (possibly {@code null}) buffer attachment object
     */
    protected BufferAttachment duplicate(Buffer original, int offset, int length,
            boolean readOnly, Class<? extends Buffer> duplicateClazz)
    {
        return this;
    }

    /**
     * Allocate a heap-backed byte buffer in the same manner as {@link ByteBuffer#allocate(int)},
     * with this object instance as its attachment.
     *
     * @param capacity the byte buffer capacity
     * @return the allocated byte buffer
     * @see ByteBuffer#allocate(int)
     */
    protected final ByteBuffer allocateByteBuffer(int capacity) {
        if (capacity < 0)
            throw Buffer.createCapacityException(capacity);
        return new HeapByteBuffer(capacity, capacity, this);
    }

    /**
     * Allocate a direct byte buffer in the same manner as {@link ByteBuffer#allocateDirect(int)},
     * with this object instance as its attachment.
     *
     * @param capacity the byte buffer capacity
     * @return the allocated byte buffer
     * @see ByteBuffer#allocateDirect(int)
     */
    protected final ByteBuffer allocateDirectByteBuffer(int capacity) {
        return new DirectByteBuffer(capacity, this);
    }

    /**
     * Wrap an array into a byte buffer in the same manner as {@link ByteBuffer#wrap(byte[], int, int)},
     * with this object instance as its attachment.
     *
     * @param bytes the byte array
     * @param off the offset into the array
     * @param len the number of bytes to include
     * @return the wrapped byte buffer
     * @see ByteBuffer#wrap(byte[], int, int)
     */
    protected final ByteBuffer wrap(byte[] bytes, int off, int len) {
        try {
            return new HeapByteBuffer(bytes, off, len, this);
        } catch (IllegalArgumentException x) {
            throw new IndexOutOfBoundsException();
        }
    }
}
