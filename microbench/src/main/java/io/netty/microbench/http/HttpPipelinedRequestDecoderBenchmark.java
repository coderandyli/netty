/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.microbench.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_INITIAL_BUFFER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;
import static io.netty.microbench.http.HttpRequestDecoderUtils.CONTENT_MIXED_DELIMITERS;

/**
 * This benchmark is based on HttpRequestDecoderTest class.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class HttpPipelinedRequestDecoderBenchmark extends AbstractMicrobenchmark {

    @Param({ "false", "true" })
    public boolean direct;

    @Param({ "1", "16" })
    public int pipeline;

    @Param({ "false", "true" })
    public boolean pooled;

    @Param({ "true", "false" })
    public boolean validateHeaders;

    private EmbeddedChannel channel;

    private ByteBuf pipelinedRequest;

    @Setup
    public void initPipeline() {
        final ByteBufAllocator allocator = pooled? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT;
        pipelinedRequest = pipelined(allocator, CONTENT_MIXED_DELIMITERS, pipeline, direct);
        channel = new EmbeddedChannel(
                new HttpRequestDecoder(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE,
                                       validateHeaders, DEFAULT_INITIAL_BUFFER_SIZE));
        // this is a trick to save doing it each time
        pipelinedRequest.retain((Integer.MAX_VALUE / 2 - 1) - pipeline);
    }

    private static ByteBuf pipelined(ByteBufAllocator alloc, byte[] content, int pipeline, boolean direct) {
        final int totalSize = pipeline * content.length;
        final ByteBuf buf = direct? alloc.directBuffer(totalSize, totalSize) : alloc.heapBuffer(totalSize, totalSize);
        for (int i = 0; i < pipeline; i++) {
            buf.writeBytes(content);
        }
        return buf;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public void testDecodeWholePipelinedRequestMixedDelimiters() {
        final EmbeddedChannel channel = this.channel;
        final ByteBuf batch = this.pipelinedRequest;
        final int refCnt = batch.refCnt();
        if (refCnt == 1) {
            batch.retain((Integer.MAX_VALUE / 2 - 1) - pipeline);
        }
        batch.resetReaderIndex();
        channel.writeInbound(batch);
        final Queue<Object> decoded = channel.inboundMessages();
        Object o;
        while ((o = decoded.poll()) != null) {
            ReferenceCountUtil.release(o);
        }
    }

    @TearDown
    public void release() {
        this.pipelinedRequest.release(pipelinedRequest.refCnt());
    }
}
