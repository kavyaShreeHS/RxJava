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

import io.reactivex.internal.disposables.DisposableHelper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.Scheduler.Worker;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.subscribers.flowable.FullArbiterSubscriber;
import io.reactivex.internal.subscriptions.*;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.SerializedSubscriber;

public final class FlowableTimeoutTimed<T> extends Flowable<T> {
    final Publisher<T> source;
    final long timeout;
    final TimeUnit unit;
    final Scheduler scheduler;
    final Publisher<? extends T> other;
    
    public FlowableTimeoutTimed(Publisher<T> source, 
            long timeout, TimeUnit unit, Scheduler scheduler, Publisher<? extends T> other) {
        this.source = source;
        this.timeout = timeout;
        this.unit = unit;
        this.scheduler = scheduler;
        this.other = other;
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        if (other == null) {
            source.subscribe(new TimeoutTimedSubscriber<T>(
                    new SerializedSubscriber<T>(s), // because errors can race 
                    timeout, unit, scheduler.createWorker()));
        } else {
            source.subscribe(new TimeoutTimedOtherSubscriber<T>(
                    s, // the FullArbiter serializes
                    timeout, unit, scheduler.createWorker(), other));
        }
    }
    
    static final class TimeoutTimedOtherSubscriber<T> implements Subscriber<T>, Disposable {
        final Subscriber<? super T> actual;
        final long timeout;
        final TimeUnit unit;
        final Scheduler.Worker worker;
        final Publisher<? extends T> other;
        
        Subscription s; 
        
        final FullArbiter<T> arbiter;

        final AtomicReference<Disposable> timer = new AtomicReference<Disposable>();

        static final Disposable NEW_TIMER = new Disposable() {
            @Override
            public void dispose() { }

            @Override
            public boolean isDisposed() {
                return true;
            }
        };

        volatile long index;
        
        volatile boolean done;
        
        public TimeoutTimedOtherSubscriber(Subscriber<? super T> actual, long timeout, TimeUnit unit, Worker worker,
                Publisher<? extends T> other) {
            this.actual = actual;
            this.timeout = timeout;
            this.unit = unit;
            this.worker = worker;
            this.other = other;
            this.arbiter = new FullArbiter<T>(actual, this, 8);
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                this.s = s;
                if (arbiter.setSubscription(s)) {
                    actual.onSubscribe(arbiter);

                    scheduleTimeout(0L);
                }
            }
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            long idx = index + 1;
            index = idx;
            
            if (arbiter.onNext(t, s)) {
                scheduleTimeout(idx);
            }
        }
        
        void scheduleTimeout(final long idx) {
            Disposable d = timer.get();
            if (d != null) {
                d.dispose();
            }
            
            if (timer.compareAndSet(d, NEW_TIMER)) {
                d = worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (idx == index) {
                            done = true;
                            s.cancel();
                            DisposableHelper.dispose(timer);
                            worker.dispose();
                            
                            if (other == null) {
                                actual.onError(new TimeoutException());
                            } else {
                                subscribeNext();
                            }
                        }
                    }
                }, timeout, unit);
                
                if (!timer.compareAndSet(NEW_TIMER, d)) {
                    d.dispose();
                }
            }
        }
        
        void subscribeNext() {
            other.subscribe(new FullArbiterSubscriber<T>(arbiter));
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            done = true;
            worker.dispose();
            DisposableHelper.dispose(timer);
            arbiter.onError(t, s);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            worker.dispose();
            DisposableHelper.dispose(timer);
            arbiter.onComplete(s);
        }
        
        @Override
        public void dispose() {
            worker.dispose();
            DisposableHelper.dispose(timer);
        }

        @Override
        public boolean isDisposed() {
            return worker.isDisposed();
        }
    }
    
    static final class TimeoutTimedSubscriber<T> implements Subscriber<T>, Disposable, Subscription {
        final Subscriber<? super T> actual;
        final long timeout;
        final TimeUnit unit;
        final Scheduler.Worker worker;
        
        Subscription s; 
        
        final AtomicReference<Disposable> timer = new AtomicReference<Disposable>();

        static final Disposable NEW_TIMER = new Disposable() {
            @Override
            public void dispose() { }

            @Override
            public boolean isDisposed() {
                return true;
            }
        };

        volatile long index;
        
        volatile boolean done;
        
        public TimeoutTimedSubscriber(Subscriber<? super T> actual, long timeout, TimeUnit unit, Worker worker) {
            this.actual = actual;
            this.timeout = timeout;
            this.unit = unit;
            this.worker = worker;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                this.s = s;
                actual.onSubscribe(s);
                scheduleTimeout(0L);
            }
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            long idx = index + 1;
            index = idx;

            actual.onNext(t);
            
            scheduleTimeout(idx);
        }
        
        void scheduleTimeout(final long idx) {
            Disposable d = timer.get();
            if (d != null) {
                d.dispose();
            }
            
            if (timer.compareAndSet(d, NEW_TIMER)) {
                d = worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (idx == index) {
                            done = true;
                            s.cancel();
                            dispose();
                            
                            actual.onError(new TimeoutException());
                        }
                    }
                }, timeout, unit);
                
                if (!timer.compareAndSet(NEW_TIMER, d)) {
                    d.dispose();
                }
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            done = true;
            dispose();
            
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            dispose();
            
            actual.onComplete();
        }
        
        @Override
        public void dispose() {
            worker.dispose();
            DisposableHelper.dispose(timer);
        }

        @Override
        public boolean isDisposed() {
            return worker.isDisposed();
        }

        @Override
        public void request(long n) {
            s.request(n);
        }
        
        @Override
        public void cancel() {
            dispose();
        }
    }
}
