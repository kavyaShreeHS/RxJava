/**
 * Copyright 2016 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.reactivestreams.*;

import io.reactivex.Flowable;
import io.reactivex.exceptions.TestException;
import io.reactivex.flowable.TestHelper;
import io.reactivex.functions.*;
import io.reactivex.internal.subscriptions.EmptySubscription;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

public class FlowableTimeoutWithSelectorTest {
    @Test(timeout = 2000)
    public void testTimeoutSelectorNormal1() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                return timeout;
            }
        };

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                return timeout;
            }
        };

        Flowable<Integer> other = Flowable.fromIterable(Arrays.asList(100));

        Subscriber<Object> o = TestHelper.mockSubscriber();
        InOrder inOrder = inOrder(o);

        source.timeout(firstTimeoutFunc, timeoutFunc, other).subscribe(o);

        source.onNext(1);
        source.onNext(2);
        source.onNext(3);
        timeout.onNext(1);

        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o).onNext(3);
        inOrder.verify(o).onNext(100);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));

    }

    @Test
    public void testTimeoutSelectorTimeoutFirst() throws InterruptedException {
        Flowable<Integer> source = Flowable.<Integer>never();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                return timeout;
            }
        };

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                return timeout;
            }
        };

        Flowable<Integer> other = Flowable.fromIterable(Arrays.asList(100));

        Subscriber<Object> o = TestHelper.mockSubscriber();
        InOrder inOrder = inOrder(o);

        source.timeout(firstTimeoutFunc, timeoutFunc, other).subscribe(o);
        
        timeout.onNext(1);
        
        inOrder.verify(o).onNext(100);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));

    }

    @Test
    public void testTimeoutSelectorFirstThrows() {
        Flowable<Integer> source = Flowable.<Integer>never();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                return timeout;
            }
        };

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                throw new TestException();
            }
        };

        Flowable<Integer> other = Flowable.fromIterable(Arrays.asList(100));

        Subscriber<Object> o = TestHelper.mockSubscriber();

        source.timeout(firstTimeoutFunc, timeoutFunc, other).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();

    }

    @Test
    public void testTimeoutSelectorSubsequentThrows() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                throw new TestException();
            }
        };

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                return timeout;
            }
        };

        Flowable<Integer> other = Flowable.fromIterable(Arrays.asList(100));

        Subscriber<Object> o = TestHelper.mockSubscriber();
        InOrder inOrder = inOrder(o);

        source.timeout(firstTimeoutFunc, timeoutFunc, other).subscribe(o);

        source.onNext(1);

        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();

    }

    @Test
    public void testTimeoutSelectorFirstObservableThrows() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                return timeout;
            }
        };

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                return Flowable.<Integer> error(new TestException());
            }
        };

        Flowable<Integer> other = Flowable.fromIterable(Arrays.asList(100));

        Subscriber<Object> o = TestHelper.mockSubscriber();

        source.timeout(firstTimeoutFunc, timeoutFunc, other).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();

    }

    @Test
    public void testTimeoutSelectorSubsequentObservableThrows() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                return Flowable.<Integer> error(new TestException());
            }
        };

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                return timeout;
            }
        };

        Flowable<Integer> other = Flowable.fromIterable(Arrays.asList(100));

        Subscriber<Object> o = TestHelper.mockSubscriber();
        InOrder inOrder = inOrder(o);

        source.timeout(firstTimeoutFunc, timeoutFunc, other).subscribe(o);

        source.onNext(1);

        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();

    }

    @Test
    public void testTimeoutSelectorWithFirstTimeoutFirstAndNoOtherObservable() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                return timeout;
            }
        };

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                return PublishProcessor.create();
            }
        };

        Subscriber<Object> o = TestHelper.mockSubscriber();
        source.timeout(firstTimeoutFunc, timeoutFunc).subscribe(o);

        timeout.onNext(1);

        InOrder inOrder = inOrder(o);
        inOrder.verify(o).onError(isA(TimeoutException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTimeoutSelectorWithTimeoutFirstAndNoOtherObservable() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        final PublishProcessor<Integer> timeout = PublishProcessor.create();

        Supplier<Flowable<Integer>> firstTimeoutFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> get() {
                return PublishProcessor.create();
            }
        };

        Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                return timeout;
            }
        };

        Subscriber<Object> o = TestHelper.mockSubscriber();
        source.timeout(firstTimeoutFunc, timeoutFunc).subscribe(o);
        source.onNext(1);

        timeout.onNext(1);

        InOrder inOrder = inOrder(o);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onError(isA(TimeoutException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTimeoutSelectorWithTimeoutAndOnNextRaceCondition() throws InterruptedException {
        // Thread 1                                    Thread 2
        //
        // observer.onNext(1)
        // start timeout
        // unsubscribe timeout in thread 2          start to do some long-time work in "unsubscribe"
        // observer.onNext(2)
        // timeout.onNext(1)
        //                                          "unsubscribe" done
        //
        //
        // In the above case, the timeout operator should ignore "timeout.onNext(1)"
        // since "observer" has already seen 2.
        final CountDownLatch observerReceivedTwo = new CountDownLatch(1);
        final CountDownLatch timeoutEmittedOne = new CountDownLatch(1);
        final CountDownLatch observerCompleted = new CountDownLatch(1);
        final CountDownLatch enteredTimeoutOne = new CountDownLatch(1);
        final AtomicBoolean latchTimeout = new AtomicBoolean(false);

        final Function<Integer, Flowable<Integer>> timeoutFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> apply(Integer t1) {
                if (t1 == 1) {
                    // Force "unsubscribe" run on another thread
                    return Flowable.create(new Publisher<Integer>() {
                        @Override
                        public void subscribe(Subscriber<? super Integer> subscriber) {
                            subscriber.onSubscribe(EmptySubscription.INSTANCE);
                            enteredTimeoutOne.countDown();
                            // force the timeout message be sent after observer.onNext(2)
                            while (true) {
                                try {
                                    if (!observerReceivedTwo.await(30, TimeUnit.SECONDS)) {
                                        // CountDownLatch timeout
                                        // There should be something wrong
                                        latchTimeout.set(true);
                                    }
                                    break;
                                } catch (InterruptedException e) {
                                    // Since we just want to emulate a busy method,
                                    // we ignore the interrupt signal from Scheduler.
                                }
                            }
                            subscriber.onNext(1);
                            timeoutEmittedOne.countDown();
                        }
                    }).subscribeOn(Schedulers.newThread());
                } else {
                    return PublishProcessor.create();
                }
            }
        };

        final Subscriber<Integer> o = TestHelper.mockSubscriber();
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                observerReceivedTwo.countDown();
                return null;
            }

        }).when(o).onNext(2);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                observerCompleted.countDown();
                return null;
            }

        }).when(o).onComplete();

        final TestSubscriber<Integer> ts = new TestSubscriber<Integer>(o);

        new Thread(new Runnable() {

            @Override
            public void run() {
                PublishProcessor<Integer> source = PublishProcessor.create();
                source.timeout(timeoutFunc, Flowable.just(3)).subscribe(ts);
                source.onNext(1); // start timeout
                try {
                    if(!enteredTimeoutOne.await(30, TimeUnit.SECONDS)) {
                        latchTimeout.set(true);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                source.onNext(2); // disable timeout
                try {
                    if(!timeoutEmittedOne.await(30, TimeUnit.SECONDS)) {
                        latchTimeout.set(true);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                source.onComplete();
            }

        }).start();

        if(!observerCompleted.await(30, TimeUnit.SECONDS)) {
            latchTimeout.set(true);
        }

        assertFalse("CoundDownLatch timeout", latchTimeout.get());

        InOrder inOrder = inOrder(o);
        inOrder.verify(o).onSubscribe((Subscription)notNull());
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o, never()).onNext(3);
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
}