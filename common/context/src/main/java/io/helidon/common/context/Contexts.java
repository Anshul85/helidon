/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.context;

import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Support for handling {@link io.helidon.common.context.Context} across thread boundaries.
 */
public final class Contexts {
    static final ThreadLocal<Stack<Context>> REGISTRY = ThreadLocal.withInitial(Stack::new);

    private Contexts() {
    }

    static void clear() {
        REGISTRY.get().clear();
    }

    static void push(Context context) {
        REGISTRY.get().push(context);
    }

    static Context pop() {
        return REGISTRY.get().pop();
    }

    /**
     * Get context registry associated with current thread.
     *
     * @return context that is associated with current thread or an empty context if none is
     */
    public static Optional<Context> context() {
        Stack<Context> contextStack = REGISTRY.get();

        if (contextStack.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(contextStack.peek());
    }

    /**
     * Wrap an executor service to correctly propagate context to its threads.
     *
     * @param toWrap executor service
     * @return a new executor service wrapping the provided one
     */
    public static ExecutorService wrap(ExecutorService toWrap) {
        // as ContextAwareScheduledExecutorImpl extends ContextAwareExecutorImpl, this is sufficient
        if (toWrap instanceof ContextAwareExecutorImpl) {
            return toWrap;
        }
        return new ContextAwareExecutorImpl(toWrap);
    }

    /**
     * Wrap a scheduled executor service to correctly propagate context to its threads.
     * Note that all scheduled methods are going to run in context of the thread scheduling the tasks.
     *
     * @param toWrap executor service
     * @return a new executor service wrapping the provided one
     */
    public static ScheduledExecutorService wrap(ScheduledExecutorService toWrap) {
        if (toWrap instanceof ContextAwareScheduledExecutorImpl) {
            return toWrap;
        }
        return new ContextAwareScheduledExecutorImpl(toWrap);
    }

    /**
     * Run the runnable in the provided context.
     * The runnable can use {@link #context()} to retrieve the context.
     *
     * @param context  context to run in
     * @param runnable runnable to execute in context
     */
    public static void runInContext(Context context, Runnable runnable) {
        push(context);
        try {
            runnable.run();
        } finally {
            pop();
        }
    }

    /**
     * Run the callable in the provided context.
     * The callable can use {@link #context()} to retrieve the context.
     *
     * @param context  context to run in
     * @param callable callable to execute in context
     * @param <T>      return type of the callable
     * @return the result of the callable
     * @throws java.lang.RuntimeException  in case the {@link java.util.concurrent.Callable#call()} threw a
     *                                          runtime exception
     */
    public static <T> T runInContext(Context context, Callable<T> callable) {
        push(context);
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutorException("Callable.call failed", e);
        } finally {
            pop();
        }
    }
}
