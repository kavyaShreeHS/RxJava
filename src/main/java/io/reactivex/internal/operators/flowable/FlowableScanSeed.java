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

import org.reactivestreams.*;

import io.reactivex.Flowable;
import io.reactivex.functions.*;
import io.reactivex.internal.queue.SpscArrayQueue;
import io.reactivex.internal.subscribers.flowable.*;
import io.reactivex.internal.subscriptions.*;
import io.reactivex.plugins.RxJavaPlugins;

public final class FlowableScanSeed<T, R> extends Flowable<R> {
    final Publisher<T> source;
    final BiFunction<R, ? super T, R> accumulator;
    final Supplier<R> seedSupplier;

    public FlowableScanSeed(Publisher<T> source, Supplier<R> seedSupplier, BiFunction<R, ? super T, R> accumulator) {
        this.source = source;
        this.accumulator = accumulator;
        this.seedSupplier = seedSupplier;
    }
    
    @Override
    protected void subscribeActual(Subscriber<? super R> s) {
        R r;
        
        try {
            r = seedSupplier.get();
        } catch (Throwable e) {
            EmptySubscription.error(e, s);
            return;
        }
        
        if (r == null) {
            EmptySubscription.error(new NullPointerException("The seed supplied is null"), s);
            return;
        }
        
        source.subscribe(new ScanSeedSubscriber<T, R>(s, accumulator, r));
    }
    
    static final class ScanSeedSubscriber<T, R> extends QueueDrainSubscriber<T, R, R> implements Subscription {
        final BiFunction<R, ? super T, R> accumulator;
        
        R value;
        
        Subscription s;
        
        public ScanSeedSubscriber(Subscriber<? super R> actual, BiFunction<R, ? super T, R> accumulator, R value) {
            super(actual, new SpscArrayQueue<R>(2));
            this.accumulator = accumulator;
            this.value = value;
            queue.offer(value);
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                this.s = s;
                actual.onSubscribe(this);
            }
        }
        
        @Override
        public void onNext(T t) {
            R v = value;
            
            R u;
            
            try {
                u = accumulator.apply(v, t);
            } catch (Throwable e) {
                s.cancel();
                onError(e);
                return;
            }
            
            if (u == null) {
                s.cancel();
                onError(new NullPointerException("The accumulator returned a null value"));
                return;
            }
            
            value = u;
            
            if (!queue.offer(u)) {
                s.cancel();
                onError(new IllegalStateException("Queue if full?!"));
                return;
            }
            drain(false);
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            error = t;
            done = true;
            drain(false);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            drain(false);
        }
        
        @Override
        public void request(long n) {
            requested(n);
            s.request(n);
            drain(false);
        }
        
        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                s.cancel();
            }
        }
        
        @Override
        public boolean accept(Subscriber<? super R> a, R v) {
            a.onNext(v);
            return true;
        }
    }
}
