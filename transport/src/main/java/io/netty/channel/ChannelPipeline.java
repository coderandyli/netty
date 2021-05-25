/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;


/**
 * A list of {@link ChannelHandler}s which handles or intercepts inbound events and outbound operations of a
 * {@link Channel}.  {@link ChannelPipeline} implements an advanced form of the
 * <a href="https://www.oracle.com/technetwork/java/interceptingfilter-142169.html">Intercepting Filter</a> pattern
 * to give a user full control over how an event is handled and how the {@link ChannelHandler}s in a pipeline
 * interact with each other.
 * ChannePipeline是 {@link ChannelHandler}的列表，用于处理或者拦截入站事件和出站操作
 *
 * <h3>Creation of a pipeline</h3>
 * pipeline的创建
 *
 * Each channel has its own pipeline and it is created automatically when a new channel is created.
 * 每个channel都拥有自己的pipeline，并且创建channel时会被自动创建
 *
 * <h3>How an event flows in a pipeline</h3>
 * 事件如何在pipeline中流动
 *
 * The following diagram(图) describes how I/O events are processed by {@link ChannelHandler}s in a {@link ChannelPipeline}
 * typically. An I/O event is handled by either a {@link ChannelInboundHandler} or a {@link ChannelOutboundHandler}
 * and be forwarded to its closest handler by calling the event propagation methods defined in
 * {@link ChannelHandlerContext}, such as {@link ChannelHandlerContext#fireChannelRead(Object)} and
 * {@link ChannelHandlerContext#write(Object)}.
 * 下图描述了ChannelHandler在ChannelPipeline中如何处理IO事件的，一个IO事件会被ChannelInboundHandler或ChannelOutboundHandler处理，并且会被转发给最近的handler
 *
 * <pre>
 *                                                 I/O Request
 *                                            via {@link Channel} or
 *                                        {@link ChannelHandlerContext}
 *                                                      |
 *  +---------------------------------------------------+---------------+
 *  |                           ChannelPipeline         |               |
 *  |                                                  \|/              |
 *  |    +---------------------+            +-----------+----------+    |
 *  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  .               |
 *  |               .                                   .               |
 *  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
 *  |        [ method call]                       [method call]         |
 *  |               .                                   .               |
 *  |               .                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  +---------------+-----------------------------------+---------------+
 *                  |                                  \|/
 *  +---------------+-----------------------------------+---------------+
 *  |               |                                   |               |
 *  |       [ Socket.read() ]                    [ Socket.write() ]     |
 *  |                                                                   |
 *  |  Netty Internal I/O Threads (Transport Implementation)            |
 *  +-------------------------------------------------------------------+
 * </pre>
 * An inbound event is handled by the inbound handlers in the bottom-up(自底向上) direction as shown on the left side of the
 * diagram.  An inbound handler usually handles the inbound data generated by the I/O thread on the bottom of the
 * diagram.  The inbound data is often read from a remote peer via the actual input operation such as
 * {@link SocketChannel#read(ByteBuffer)}.  If an inbound event goes beyond the top inbound handler, it is discarded
 * silently, or logged if it needs your attention.
 * 如上图的左侧所示，一个入站事件被入站处理器通过自底向上的方向处理，入站处理器通常通过上图所示的IO线程处理入站数据。入站数据通常通过实际的输入操作
 * 从远程对等端读取，例如{@link SocketChannel#read(ByteBuffer)}。如果入站事件超出了顶级入站处理程序的范围，则将悄然丢弃该事件，如果需要注意，则记录该事件。
 *
 * <p>
 * An outbound event is handled by the outbound handler in the top-down（自上而下） direction as shown on the right side of the
 * diagram.  An outbound handler usually generates or transforms the outbound traffic such as write requests.
 * If an outbound event [goes beyond](超出、超越) the bottom outbound handler, it is handled by an I/O thread associated with the
 * {@link Channel}. The I/O thread often performs the actual output operation such as
 * {@link SocketChannel#write(ByteBuffer)}.
 * 如上图右侧所示，一个出站事件被出站处理器通过自上而下的方向处理，出站处理程序通常生成或转换出站流量，如写请求。如果出站事件，超过了底端出站处理器的处理范围，
 * 它将由一个与@link Channel}关联的I/O线程处理。I/O线程通常执行实际的输出操作，例如{@link SocketChannel#write(ByteBuffer)}。
 *
 * <p>
 * For example, let us assume that we created the following pipeline:
 * 示例：创建以下pipeline
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Inbound} means it is an inbound handler.
 * The class whose name starts with {@code Outbound} means it is a outbound handler.
 * 上述示例中，以"Inbound"开头的类名，表示入站处理器，以"Outbound"开头的类名，表示出站处理器，
 * <p>
 * In the given example configuration, the handler evaluation order is 1, 2, 3, 4, 5 when an event goes inbound.
 * When an event goes outbound, the order is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation(评估) of certain（确信、必然的） handlers to shorten the stack depth:
 * 示例中的配置，当入站的时候预估的顺序是1, 2, 3, 4, 5，出站的顺序是5, 4, 3, 2, 1。基于这个原则，ChannelPipeline会跳过一些handler,
 * 从而缩短堆栈的深度.
 * <ul>
 * <li>3 and 4 don't implement {@link ChannelInboundHandler}, and therefore the actual evaluation order of an inbound
 *     event will be: 1, 2, and 5.</li>
 *     3、4没有实现ChannelInboundHandler，因此真实的入站顺序是1，2，5
 * <li>1 and 2 don't implement {@link ChannelOutboundHandler}, and therefore the actual evaluation order of a
 *     outbound event will be: 5, 4, and 3.</li>
 *     1，2 没有实现ChannelOutboundHandler，因此真实的出站顺序是5，4，3
 * <li>If 5 implements both {@link ChannelInboundHandler} and {@link ChannelOutboundHandler}, the evaluation order of
 *     an inbound and a outbound event could be 125 and 543 respectively.</li>
 *     5即实现了ChannelInboundHandler也实现了ChannelOutboundHandler，因此入站、出站都不会跳过。
 * </ul>
 *
 * <h3>Forwarding an event to the next handler</h3>
 * 转发事件到下一个处理器
 *
 * As you might noticed in the diagram shows, a handler has to invoke the event propagation(传播) methods in
 * {@link ChannelHandlerContext} to forward an event to its next handler.  Those methods include:
 * 你可能注意到上图，处理器不得不调用{@link ChannelHandlerContext}中事件【传播方法】来转发事件到下一个处理器中。
 * <ul>
 * <li>Inbound event propagation methods:
 *      入站事件【传播方法】
 *     <ul>
 *     <li>{@link ChannelHandlerContext#fireChannelRegistered()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelActive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelRead(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelReadComplete()}</li>
 *     <li>{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}</li>
 *     <li>{@link ChannelHandlerContext#fireUserEventTriggered(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelWritabilityChanged()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelInactive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelUnregistered()}</li>
 *     </ul>
 * </li>
 * <li>Outbound event propagation methods:
 *      出站事件【传播方法】
 *     <ul>
 *     <li>{@link ChannelHandlerContext#bind(SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#write(Object, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#flush()}</li>
 *     <li>{@link ChannelHandlerContext#read()}</li>
 *     <li>{@link ChannelHandlerContext#disconnect(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#close(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#deregister(ChannelPromise)}</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * and the following example shows how the event propagation is usually done:
 *
 * <pre>
 * public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         System.out.println("Connected!");
 *         ctx.fireChannelActive();
 *     }
 * }
 *
 * public class MyOutboundHandler extends {@link ChannelOutboundHandlerAdapter} {
 *     {@code @Override}
 *     public void close({@link ChannelHandlerContext} ctx, {@link ChannelPromise} promise) {
 *         System.out.println("Closing ..");
 *         ctx.close(promise);
 *     }
 * }
 * </pre>
 *
 * <h3>Building a pipeline</h3>
 *     构建一个pipeline
 * <p>
 * A user is supposed(应该) to have one or more {@link ChannelHandler}s in a pipeline to receive I/O events (e.g. read) and
 * to request I/O operations (e.g. write and close).  For example, a typical(典型的) server will have the following handlers
 * in each channel's pipeline, but your mileage may vary depending on the complexity and characteristics of the
 * protocol and business logic:
 * 一个用户应该在一个pipeline中有有一个或多个ChannelHandler来接收IO事件和请求I/O操作(例如写和关闭)。例如，典型的服务器在每个channel的pipeline中都有以下处理程序
 *
 * <ol>
 * <li>Protocol Decoder - translates binary data (e.g. {@link ByteBuf}) into a Java object.</li>
 *     协议解码器 - 将二进制数据(例如 ByteBuf)转换为java对象
 * <li>Protocol Encoder - translates a Java object into binary data.</li>
 *     协议编码器 - 将java对象转换成二进制数据
 * <li>Business Logic Handler - performs the actual business logic (e.g. database access).</li>
 *     业务逻辑处理器 - 执行真实的业务逻辑
 * </ol>
 *
 * and it could be represented as shown in the following example:
 * 可以用以下示例表示
 *
 * <pre>
 * static final {@link EventExecutorGroup} group = new {@link DefaultEventExecutorGroup}(16);
 * ...
 *
 * {@link ChannelPipeline} pipeline = ch.pipeline();
 *
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 *
 * // Tell the pipeline to run MyBusinessLogicHandler's event handler methods
 * // in a different thread than an I/O thread so that the I/O thread is not blocked by
 * // a time-consuming task.
 * // If your business logic is fully asynchronous or finished very quickly, you don't
 * // need to specify a group.
 * pipeline.addLast(group, "handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * Be aware that while using {@link DefaultEventLoopGroup} will offload the operation from the {@link EventLoop} it will
 * still process tasks in a serial fashion per {@link ChannelHandlerContext} and so guarantee ordering. Due the ordering
 * it may still become a bottle-neck. If ordering is not a requirement for your use-case you may want to consider using
 * {@link UnorderedThreadPoolEventExecutor} to maximize the parallelism of the task execution.
 *
 * <h3>Thread safety</h3>
 * 线程安全的
 * <p>
 * A {@link ChannelHandler} can be added or removed at any time because a {@link ChannelPipeline} is thread safe.
 * For example, you can insert an encryption handler when sensitive information is about to be exchanged, and remove it
 * after the exchange.
 *
 * 【ChannePipeline是 {@link ChannelHandler}的列表，用于处理或者拦截入站事件和出站操作】
 * 默认实现类{@link DefaultChannelPipeline}
 * 基于【职责链模式】
 */
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addLast(String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     * 添加ChannelHandler到pipeline的最后位置
     * - ChannelHandler将在IO线程组中执行，如想指定单独的线程组，可通过{@link #addLast(EventExecutorGroup, ChannelHandler...)}指定线程组
     *
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     * 插入ChannelHandler到最后位置（可指定线程组执行）
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods. ChannelHandler将在该线程组中执行 ( 默认在IO线程组中执行)
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline.
     *
     * @param  handler          the {@link ChannelHandler} to remove
     *
     * @throws NoSuchElementException
     *         if there's no such handler in this pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this pipeline.
     *
     * @param  name             the name under which the {@link ChannelHandler} was stored.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException
     *         if the specified name is {@code null}
     */
    ChannelHandler remove(String name);

    /**
     * Removes the {@link ChannelHandler} of the specified type from this pipeline.
     *
     * @param <T>           the type of the handler
     * @param handlerType   the type of the handler
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException
     *         if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in this pipeline.
     *
     * @param  oldHandler    the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return itself

     * @throws NoSuchElementException
     *         if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new handler in this pipeline.
     *
     * @param  oldName       the name of the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new handler in this pipeline.
     *
     * @param  oldHandlerType   the type of the handler to be removed
     * @param  newName          the name under which the replacement should be added
     * @param  newHandler       the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler of the specified old handler type does not exist
     *         in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                         ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler first();

    /**
     * Returns the context of the first {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext firstContext();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler last();

    /**
     * Returns the context of the last {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext lastContext();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     *
     * @return the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     *
     * @return the handler of the specified handler type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     *
     * @return the context object of the specified handler.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     *
     * @return the context object of the handler of the specified type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel channel();

    /**
     * Returns the {@link List} of the handler names.
     */
    List<String> names();

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     */
    Map<String, ChannelHandler> toMap();

    @Override
    ChannelPipeline fireChannelRegistered();

    @Override
    ChannelPipeline fireChannelUnregistered();

    @Override
    ChannelPipeline fireChannelActive();

    @Override
    ChannelPipeline fireChannelInactive();

    @Override
    ChannelPipeline fireExceptionCaught(Throwable cause);

    @Override
    ChannelPipeline fireUserEventTriggered(Object event);

    @Override
    ChannelPipeline fireChannelRead(Object msg);

    @Override
    ChannelPipeline fireChannelReadComplete();

    @Override
    ChannelPipeline fireChannelWritabilityChanged();

    @Override
    ChannelPipeline flush();
}
