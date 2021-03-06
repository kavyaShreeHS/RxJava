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

package io.reactivex.internal.operators.observable;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.Test;
import org.mockito.InOrder;

import io.reactivex.*;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.*;
import io.reactivex.flowable.TestHelper;
import io.reactivex.functions.Function;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.observers.*;
import io.reactivex.schedulers.*;
import io.reactivex.subjects.*;

public class ObservableConcatTest {

    @Test
    public void testConcat() {
        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };

        final Observable<String> odds = Observable.fromArray(o);
        final Observable<String> even = Observable.fromArray(e);

        Observable<String> concat = Observable.concat(odds, even);
        concat.subscribe(NbpObserver);

        verify(NbpObserver, times(7)).onNext(anyString());
    }

    @Test
    public void testConcatWithList() {
        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };

        final Observable<String> odds = Observable.fromArray(o);
        final Observable<String> even = Observable.fromArray(e);
        final List<Observable<String>> list = new ArrayList<Observable<String>>();
        list.add(odds);
        list.add(even);
        Observable<String> concat = Observable.concat(Observable.fromIterable(list));
        concat.subscribe(NbpObserver);

        verify(NbpObserver, times(7)).onNext(anyString());
    }

    @Test
    public void testConcatObservableOfObservables() {
        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };

        final Observable<String> odds = Observable.fromArray(o);
        final Observable<String> even = Observable.fromArray(e);

        Observable<Observable<String>> observableOfObservables = Observable.create(new ObservableConsumable<Observable<String>>() {

            @Override
            public void subscribe(Observer<? super Observable<String>> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                // simulate what would happen in an NbpObservable
                NbpObserver.onNext(odds);
                NbpObserver.onNext(even);
                NbpObserver.onComplete();
            }

        });
        Observable<String> concat = Observable.concat(observableOfObservables);

        concat.subscribe(NbpObserver);

        verify(NbpObserver, times(7)).onNext(anyString());
    }

    /**
     * Simple concat of 2 asynchronous observables ensuring it emits in correct order.
     */
    @Test
    public void testSimpleAsyncConcat() {
        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();

        TestObservable<String> o1 = new TestObservable<String>("one", "two", "three");
        TestObservable<String> o2 = new TestObservable<String>("four", "five", "six");

        Observable.concat(Observable.create(o1), Observable.create(o2)).subscribe(NbpObserver);

        try {
            // wait for async observables to complete
            o1.t.join();
            o2.t.join();
        } catch (Throwable e) {
            throw new RuntimeException("failed waiting on threads");
        }

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("one");
        inOrder.verify(NbpObserver, times(1)).onNext("two");
        inOrder.verify(NbpObserver, times(1)).onNext("three");
        inOrder.verify(NbpObserver, times(1)).onNext("four");
        inOrder.verify(NbpObserver, times(1)).onNext("five");
        inOrder.verify(NbpObserver, times(1)).onNext("six");
    }

    @Test
    public void testNestedAsyncConcatLoop() throws Throwable {
        for (int i = 0; i < 500; i++) {
            if (i % 10 == 0) {
                System.out.println("testNestedAsyncConcat >> " + i);
            }
            testNestedAsyncConcat();
        }
    }
    
    /**
     * Test an async NbpObservable that emits more async Observables
     * @throws InterruptedException if the test is interrupted
     */
    @Test
    public void testNestedAsyncConcat() throws InterruptedException {
        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();

        final TestObservable<String> o1 = new TestObservable<String>("one", "two", "three");
        final TestObservable<String> o2 = new TestObservable<String>("four", "five", "six");
        final TestObservable<String> o3 = new TestObservable<String>("seven", "eight", "nine");
        final CountDownLatch allowThird = new CountDownLatch(1);

        final AtomicReference<Thread> parent = new AtomicReference<Thread>();
        final CountDownLatch parentHasStarted = new CountDownLatch(1);
        final CountDownLatch parentHasFinished = new CountDownLatch(1);
        
        
        Observable<Observable<String>> observableOfObservables = Observable.create(new ObservableConsumable<Observable<String>>() {

            @Override
            public void subscribe(final Observer<? super Observable<String>> NbpObserver) {
                final Disposable d = Disposables.empty();
                NbpObserver.onSubscribe(d);
                parent.set(new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            // emit first
                            if (!d.isDisposed()) {
                                System.out.println("Emit o1");
                                NbpObserver.onNext(Observable.create(o1));
                            }
                            // emit second
                            if (!d.isDisposed()) {
                                System.out.println("Emit o2");
                                NbpObserver.onNext(Observable.create(o2));
                            }

                            // wait until sometime later and emit third
                            try {
                                allowThird.await();
                            } catch (InterruptedException e) {
                                NbpObserver.onError(e);
                            }
                            if (!d.isDisposed()) {
                                System.out.println("Emit o3");
                                NbpObserver.onNext(Observable.create(o3));
                            }

                        } catch (Throwable e) {
                            NbpObserver.onError(e);
                        } finally {
                            System.out.println("Done parent NbpObservable");
                            NbpObserver.onComplete();
                            parentHasFinished.countDown();
                        }
                    }
                }));
                parent.get().start();
                parentHasStarted.countDown();
            }
        });

        Observable.concat(observableOfObservables).subscribe(NbpObserver);

        // wait for parent to start
        parentHasStarted.await();

        try {
            // wait for first 2 async observables to complete
            System.out.println("Thread1 is starting ... waiting for it to complete ...");
            o1.waitForThreadDone();
            System.out.println("Thread2 is starting ... waiting for it to complete ...");
            o2.waitForThreadDone();
        } catch (Throwable e) {
            throw new RuntimeException("failed waiting on threads", e);
        }

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("one");
        inOrder.verify(NbpObserver, times(1)).onNext("two");
        inOrder.verify(NbpObserver, times(1)).onNext("three");
        inOrder.verify(NbpObserver, times(1)).onNext("four");
        inOrder.verify(NbpObserver, times(1)).onNext("five");
        inOrder.verify(NbpObserver, times(1)).onNext("six");
        // we shouldn't have the following 3 yet
        inOrder.verify(NbpObserver, never()).onNext("seven");
        inOrder.verify(NbpObserver, never()).onNext("eight");
        inOrder.verify(NbpObserver, never()).onNext("nine");
        // we should not be completed yet
        verify(NbpObserver, never()).onComplete();
        verify(NbpObserver, never()).onError(any(Throwable.class));

        // now allow the third
        allowThird.countDown();

        try {
            // wait for 3rd to complete
            o3.waitForThreadDone();
        } catch (Throwable e) {
            throw new RuntimeException("failed waiting on threads", e);
        }

        try {
            // wait for the parent to complete
            if (!parentHasFinished.await(5, TimeUnit.SECONDS)) {
                fail("Parent didn't finish within the time limit");
            }
        } catch (Throwable e) {
            throw new RuntimeException("failed waiting on threads", e);
        }
        
        inOrder.verify(NbpObserver, times(1)).onNext("seven");
        inOrder.verify(NbpObserver, times(1)).onNext("eight");
        inOrder.verify(NbpObserver, times(1)).onNext("nine");

        verify(NbpObserver, never()).onError(any(Throwable.class));
        inOrder.verify(NbpObserver, times(1)).onComplete();
    }

    @Test
    public void testBlockedObservableOfObservables() {
        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };
        final Observable<String> odds = Observable.fromArray(o);
        final Observable<String> even = Observable.fromArray(e);
        final CountDownLatch callOnce = new CountDownLatch(1);
        final CountDownLatch okToContinue = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        TestObservable<Observable<String>> observableOfObservables = new TestObservable<Observable<String>>(callOnce, okToContinue, odds, even);
        Observable<String> concatF = Observable.concat(Observable.create(observableOfObservables));
        concatF.subscribe(NbpObserver);
        try {
            //Block main thread to allow observables to serve up o1.
            callOnce.await();
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        // The concated NbpObservable should have served up all of the odds.
        verify(NbpObserver, times(1)).onNext("1");
        verify(NbpObserver, times(1)).onNext("3");
        verify(NbpObserver, times(1)).onNext("5");
        verify(NbpObserver, times(1)).onNext("7");

        try {
            // unblock observables so it can serve up o2 and complete
            okToContinue.countDown();
            observableOfObservables.t.join();
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        // The concatenated NbpObservable should now have served up all the evens.
        verify(NbpObserver, times(1)).onNext("2");
        verify(NbpObserver, times(1)).onNext("4");
        verify(NbpObserver, times(1)).onNext("6");
    }

    @Test
    public void testConcatConcurrentWithInfinity() {
        final TestObservable<String> w1 = new TestObservable<String>("one", "two", "three");
        //This NbpObservable will send "hello" MAX_VALUE time.
        final TestObservable<String> w2 = new TestObservable<String>("hello", Integer.MAX_VALUE);

        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();
        
        @SuppressWarnings("unchecked")
        TestObservable<Observable<String>> observableOfObservables = new TestObservable<Observable<String>>(Observable.create(w1), Observable.create(w2));
        Observable<String> concatF = Observable.concat(Observable.create(observableOfObservables));

        concatF.take(50).subscribe(NbpObserver);

        //Wait for the thread to start up.
        try {
            w1.waitForThreadDone();
            w2.waitForThreadDone();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("one");
        inOrder.verify(NbpObserver, times(1)).onNext("two");
        inOrder.verify(NbpObserver, times(1)).onNext("three");
        inOrder.verify(NbpObserver, times(47)).onNext("hello");
        verify(NbpObserver, times(1)).onComplete();
        verify(NbpObserver, never()).onError(any(Throwable.class));
    }

    @Test
    public void testConcatNonBlockingObservables() {

        final CountDownLatch okToContinueW1 = new CountDownLatch(1);
        final CountDownLatch okToContinueW2 = new CountDownLatch(1);

        final TestObservable<String> w1 = new TestObservable<String>(null, okToContinueW1, "one", "two", "three");
        final TestObservable<String> w2 = new TestObservable<String>(null, okToContinueW2, "four", "five", "six");

        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();
        
        Observable<Observable<String>> observableOfObservables = Observable.create(new ObservableConsumable<Observable<String>>() {

            @Override
            public void subscribe(Observer<? super Observable<String>> NbpObserver) {
                NbpObserver.onSubscribe(EmptyDisposable.INSTANCE);
                // simulate what would happen in an NbpObservable
                NbpObserver.onNext(Observable.create(w1));
                NbpObserver.onNext(Observable.create(w2));
                NbpObserver.onComplete();
            }

        });
        Observable<String> concat = Observable.concat(observableOfObservables);
        concat.subscribe(NbpObserver);

        verify(NbpObserver, times(0)).onComplete();

        try {
            // release both threads
            okToContinueW1.countDown();
            okToContinueW2.countDown();
            // wait for both to finish
            w1.t.join();
            w2.t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("one");
        inOrder.verify(NbpObserver, times(1)).onNext("two");
        inOrder.verify(NbpObserver, times(1)).onNext("three");
        inOrder.verify(NbpObserver, times(1)).onNext("four");
        inOrder.verify(NbpObserver, times(1)).onNext("five");
        inOrder.verify(NbpObserver, times(1)).onNext("six");
        verify(NbpObserver, times(1)).onComplete();

    }

    /**
     * Test unsubscribing the concatenated NbpObservable in a single thread.
     */
    @Test
    public void testConcatUnsubscribe() {
        final CountDownLatch callOnce = new CountDownLatch(1);
        final CountDownLatch okToContinue = new CountDownLatch(1);
        final TestObservable<String> w1 = new TestObservable<String>("one", "two", "three");
        final TestObservable<String> w2 = new TestObservable<String>(callOnce, okToContinue, "four", "five", "six");

        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();
        TestObserver<String> ts = new TestObserver<String>(NbpObserver);

        final Observable<String> concat = Observable.concat(Observable.create(w1), Observable.create(w2));

        try {
            // Subscribe
            concat.subscribe(ts);
            //Block main thread to allow NbpObservable "w1" to complete and NbpObservable "w2" to call onNext once.
            callOnce.await();
            // Unsubcribe
            ts.dispose();
            //Unblock the NbpObservable to continue.
            okToContinue.countDown();
            w1.t.join();
            w2.t.join();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("one");
        inOrder.verify(NbpObserver, times(1)).onNext("two");
        inOrder.verify(NbpObserver, times(1)).onNext("three");
        inOrder.verify(NbpObserver, times(1)).onNext("four");
        inOrder.verify(NbpObserver, never()).onNext("five");
        inOrder.verify(NbpObserver, never()).onNext("six");
        inOrder.verify(NbpObserver, never()).onComplete();

    }

    /**
     * All observables will be running in different threads so subscribe() is unblocked. CountDownLatch is only used in order to call unsubscribe() in a predictable manner.
     */
    @Test
    public void testConcatUnsubscribeConcurrent() {
        final CountDownLatch callOnce = new CountDownLatch(1);
        final CountDownLatch okToContinue = new CountDownLatch(1);
        final TestObservable<String> w1 = new TestObservable<String>("one", "two", "three");
        final TestObservable<String> w2 = new TestObservable<String>(callOnce, okToContinue, "four", "five", "six");

        Observer<String> NbpObserver = TestHelper.mockNbpSubscriber();
        TestObserver<String> ts = new TestObserver<String>(NbpObserver);
        
        @SuppressWarnings("unchecked")
        TestObservable<Observable<String>> observableOfObservables = new TestObservable<Observable<String>>(Observable.create(w1), Observable.create(w2));
        Observable<String> concatF = Observable.concat(Observable.create(observableOfObservables));

        concatF.subscribe(ts);

        try {
            //Block main thread to allow NbpObservable "w1" to complete and NbpObservable "w2" to call onNext exactly once.
            callOnce.await();
            //"four" from w2 has been processed by onNext()
            ts.dispose();
            //"five" and "six" will NOT be processed by onNext()
            //Unblock the NbpObservable to continue.
            okToContinue.countDown();
            w1.t.join();
            w2.t.join();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        InOrder inOrder = inOrder(NbpObserver);
        inOrder.verify(NbpObserver, times(1)).onNext("one");
        inOrder.verify(NbpObserver, times(1)).onNext("two");
        inOrder.verify(NbpObserver, times(1)).onNext("three");
        inOrder.verify(NbpObserver, times(1)).onNext("four");
        inOrder.verify(NbpObserver, never()).onNext("five");
        inOrder.verify(NbpObserver, never()).onNext("six");
        verify(NbpObserver, never()).onComplete();
        verify(NbpObserver, never()).onError(any(Throwable.class));
    }

    private static class TestObservable<T> implements ObservableConsumable<T> {

        private final Disposable s = new Disposable() {
            @Override
            public void dispose() {
                    subscribed = false;
            }

            @Override
            public boolean isDisposed() {
                return subscribed;
            }
        };
        private final List<T> values;
        private Thread t = null;
        private int count = 0;
        private volatile boolean subscribed = true;
        private final CountDownLatch once;
        private final CountDownLatch okToContinue;
        private final CountDownLatch threadHasStarted = new CountDownLatch(1);
        private final T seed;
        private final int size;

        public TestObservable(T... values) {
            this(null, null, values);
        }

        public TestObservable(CountDownLatch once, CountDownLatch okToContinue, T... values) {
            this.values = Arrays.asList(values);
            this.size = this.values.size();
            this.once = once;
            this.okToContinue = okToContinue;
            this.seed = null;
        }

        public TestObservable(T seed, int size) {
            values = null;
            once = null;
            okToContinue = null;
            this.seed = seed;
            this.size = size;
        }

        @Override
        public void subscribe(final Observer<? super T> NbpObserver) {
            NbpObserver.onSubscribe(s);
            t = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        while (count < size && subscribed) {
                            if (null != values)
                                NbpObserver.onNext(values.get(count));
                            else
                                NbpObserver.onNext(seed);
                            count++;
                            //Unblock the main thread to call unsubscribe.
                            if (null != once)
                                once.countDown();
                            //Block until the main thread has called unsubscribe.
                            if (null != okToContinue)
                                okToContinue.await(5, TimeUnit.SECONDS);
                        }
                        if (subscribed)
                            NbpObserver.onComplete();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        fail(e.getMessage());
                    }
                }

            });
            t.start();
            threadHasStarted.countDown();
        }

        void waitForThreadDone() throws InterruptedException {
            threadHasStarted.await();
            t.join();
        }
    }

    @Test
    public void testMultipleObservers() {
        Observer<Object> o1 = TestHelper.mockNbpSubscriber();
        Observer<Object> o2 = TestHelper.mockNbpSubscriber();

        TestScheduler s = new TestScheduler();

        Observable<Long> timer = Observable.interval(500, TimeUnit.MILLISECONDS, s).take(2);
        Observable<Long> o = Observable.concat(timer, timer);

        o.subscribe(o1);
        o.subscribe(o2);

        InOrder inOrder1 = inOrder(o1);
        InOrder inOrder2 = inOrder(o2);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(0L);
        inOrder2.verify(o2, times(1)).onNext(0L);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(1L);
        inOrder2.verify(o2, times(1)).onNext(1L);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(0L);
        inOrder2.verify(o2, times(1)).onNext(0L);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(1L);
        inOrder2.verify(o2, times(1)).onNext(1L);

        inOrder1.verify(o1, times(1)).onComplete();
        inOrder2.verify(o2, times(1)).onComplete();

        verify(o1, never()).onError(any(Throwable.class));
        verify(o2, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void concatVeryLongObservableOfObservables() {
        final int n = 10000;
        Observable<Observable<Integer>> source = Observable.range(0, n).map(new Function<Integer, Observable<Integer>>() {
            @Override
            public Observable<Integer> apply(Integer v) {
                return Observable.just(v);
            }
        });
        
        Observable<List<Integer>> result = Observable.concat(source).toList();
        
        Observer<List<Integer>> o = TestHelper.mockNbpSubscriber();
        InOrder inOrder = inOrder(o);
        
        result.subscribe(o);

        List<Integer> list = new ArrayList<Integer>(n);
        for (int i = 0; i < n; i++) {
            list.add(i);
        }
        inOrder.verify(o).onNext(list);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    @Test
    public void concatVeryLongObservableOfObservablesTakeHalf() {
        final int n = 10000;
        Observable<Observable<Integer>> source = Observable.range(0, n).map(new Function<Integer, Observable<Integer>>() {
            @Override
            public Observable<Integer> apply(Integer v) {
                return Observable.just(v);
            }
        });
        
        Observable<List<Integer>> result = Observable.concat(source).take(n / 2).toList();
        
        Observer<List<Integer>> o = TestHelper.mockNbpSubscriber();
        InOrder inOrder = inOrder(o);
        
        result.subscribe(o);

        List<Integer> list = new ArrayList<Integer>(n);
        for (int i = 0; i < n / 2; i++) {
            list.add(i);
        }
        inOrder.verify(o).onNext(list);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void testConcatOuterBackpressure() {
        assertEquals(1,
                (int) Observable.<Integer> empty()
                        .concatWith(Observable.just(1))
                        .take(1)
                        .toBlocking().single());
    }
    
    // https://github.com/ReactiveX/RxJava/issues/1818
    @Test
    public void testConcatWithNonCompliantSourceDoubleOnComplete() {
        Observable<String> o = Observable.create(new ObservableConsumable<String>() {

            @Override
            public void subscribe(Observer<? super String> s) {
                s.onSubscribe(EmptyDisposable.INSTANCE);
                s.onNext("hello");
                s.onComplete();
                s.onComplete();
            }
            
        });
        
        TestObserver<String> ts = new TestObserver<String>();
        Observable.concat(o, o).subscribe(ts);
        ts.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);
        ts.assertTerminated();
        ts.assertNoErrors();
        ts.assertValues("hello", "hello");
    }

    @Test(timeout = 30000)
    public void testIssue2890NoStackoverflow() throws InterruptedException {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final Scheduler sch = Schedulers.from(executor);

        Function<Integer, Observable<Integer>> func = new Function<Integer, Observable<Integer>>() {
            @Override
            public Observable<Integer> apply(Integer t) {
                Observable<Integer> o = Observable.just(t)
                        .subscribeOn(sch)
                ;
                Subject<Integer> subject = UnicastSubject.create();
                o.subscribe(subject);
                return subject;
            }
        };

        int n = 5000;
        final AtomicInteger counter = new AtomicInteger();

        Observable.range(1, n)
        .concatMap(func).subscribe(new DefaultObserver<Integer>() {
            @Override
            public void onNext(Integer t) {
                // Consume after sleep for 1 ms
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // ignored
                }
                if (counter.getAndIncrement() % 100 == 0) {
                    System.out.println("testIssue2890NoStackoverflow -> " + counter.get());
                };
            }

            @Override
            public void onComplete() {
                executor.shutdown();
            }

            @Override
            public void onError(Throwable e) {
                executor.shutdown();
            }
        });

        executor.awaitTermination(20000, TimeUnit.MILLISECONDS);
        
        assertEquals(n, counter.get());
    }
    
    @Test//(timeout = 100000)
    public void concatMapRangeAsyncLoopIssue2876() {
        final long durationSeconds = 2;
        final long startTime = System.currentTimeMillis();
        for (int i = 0;; i++) {
            //only run this for a max of ten seconds
            if (System.currentTimeMillis()-startTime > TimeUnit.SECONDS.toMillis(durationSeconds))
                return;
            if (i % 1000 == 0) {
                System.out.println("concatMapRangeAsyncLoop > " + i);
            }
            TestObserver<Integer> ts = new TestObserver<Integer>();
            Observable.range(0, 1000)
            .concatMap(new Function<Integer, Observable<Integer>>() {
                @Override
                public Observable<Integer> apply(Integer t) {
                    return Observable.fromIterable(Arrays.asList(t));
                }
            })
            .observeOn(Schedulers.computation()).subscribe(ts);

            ts.awaitTerminalEvent(2500, TimeUnit.MILLISECONDS);
            ts.assertTerminated();
            ts.assertNoErrors();
            assertEquals(1000, ts.valueCount());
            assertEquals((Integer)999, ts.values().get(999));
        }
    }
    
}