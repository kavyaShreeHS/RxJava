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

import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.*;

import io.reactivex.Flowable;
import io.reactivex.internal.subscriptions.*;
import io.reactivex.subscribers.SerializedSubscriber;

public final class FlowableTakeUntil<T, U> extends Flowable<T> {
    final Publisher<T> source;
    final Publisher<? extends U> other;
    public FlowableTakeUntil(Publisher<T> source, Publisher<? extends U> other) {
        this.source = source;
        this.other = other;
    }
    
    @Override
    protected void subscribeActual(Subscriber<? super T> child) {
        final SerializedSubscriber<T> serial = new SerializedSubscriber<T>(child);
        
        final ArrayCompositeSubscription frc = new ArrayCompositeSubscription(2);
        
        final TakeUntilSubscriber<T> tus = new TakeUntilSubscriber<T>(serial, frc); 
        
        other.subscribe(new Subscriber<U>() {
            @Override
            public void onSubscribe(Subscription s) {
                if (frc.setResource(1, s)) {
                    s.request(Long.MAX_VALUE);
                }
            }
            @Override
            public void onNext(U t) {
                frc.dispose();
                if (tus.compareAndSet(false, true)) {
                    EmptySubscription.complete(serial);
                } else {
                    serial.onComplete();
                }
            }
            @Override
            public void onError(Throwable t) {
                frc.dispose();
                if (tus.compareAndSet(false, true)) {
                    EmptySubscription.error(t, serial);
                } else {
                    serial.onError(t);
                }
            }
            @Override
            public void onComplete() {
                frc.dispose();
                if (tus.compareAndSet(false, true)) {
                    EmptySubscription.complete(serial);
                } else {
                    serial.onComplete();
                }
            }
        });
        
        source.subscribe(tus);
    }
    
    static final class TakeUntilSubscriber<T> extends AtomicBoolean implements Subscriber<T>, Subscription {
        /** */
        private static final long serialVersionUID = 3451719290311127173L;
        final Subscriber<? super T> actual;
        final ArrayCompositeSubscription frc;
        
        Subscription s;
        
        public TakeUntilSubscriber(Subscriber<? super T> actual, ArrayCompositeSubscription frc) {
            this.actual = actual;
            this.frc = frc;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                this.s = s;
                if (frc.setResource(0, s)) {
                    if (compareAndSet(false, true)) {
                        actual.onSubscribe(this);
                    }
                }
            }
        }
        
        @Override
        public void onNext(T t) {
            actual.onNext(t);
        }
        
        @Override
        public void onError(Throwable t) {
            frc.dispose();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            frc.dispose();
            actual.onComplete();
        }
        
        @Override
        public void request(long n) {
            s.request(n);
        }
        
        @Override
        public void cancel() {
            frc.dispose();
        }
    }
}
