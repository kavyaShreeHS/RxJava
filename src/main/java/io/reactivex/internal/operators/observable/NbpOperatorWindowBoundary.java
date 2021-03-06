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

import java.util.Queue;
import java.util.concurrent.atomic.*;

import io.reactivex.*;
import io.reactivex.Observable.NbpOperator;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.internal.queue.MpscLinkedQueue;
import io.reactivex.internal.subscribers.observable.*;
import io.reactivex.internal.util.NotificationLite;
import io.reactivex.observers.SerializedObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subjects.UnicastSubject;

public final class NbpOperatorWindowBoundary<T, B> implements NbpOperator<Observable<T>, T> {
    final ObservableConsumable<B> other;
    final int bufferSize;
    
    public NbpOperatorWindowBoundary(ObservableConsumable<B> other, int bufferSize) {
        this.other = other;
        this.bufferSize = bufferSize;
    }
    
    @Override
    public Observer<? super T> apply(Observer<? super Observable<T>> t) {
        return new WindowBoundaryMainSubscriber<T, B>(new SerializedObserver<Observable<T>>(t), other, bufferSize);
    }
    
    static final class WindowBoundaryMainSubscriber<T, B> 
    extends NbpQueueDrainSubscriber<T, Object, Observable<T>> 
    implements Disposable {
        
        final ObservableConsumable<B> other;
        final int bufferSize;
        
        Disposable s;
        
        final AtomicReference<Disposable> boundary = new AtomicReference<Disposable>();
        
        UnicastSubject<T> window;
        
        static final Object NEXT = new Object();
        
        final AtomicLong windows = new AtomicLong();
        
        public WindowBoundaryMainSubscriber(Observer<? super Observable<T>> actual, ObservableConsumable<B> other,
                int bufferSize) {
            super(actual, new MpscLinkedQueue<Object>());
            this.other = other;
            this.bufferSize = bufferSize;
            windows.lazySet(1);
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (DisposableHelper.validate(this.s, s)) {
                this.s = s;
                
                Observer<? super Observable<T>> a = actual;
                a.onSubscribe(this);
                
                if (cancelled) {
                    return;
                }
                
                UnicastSubject<T> w = UnicastSubject.create(bufferSize);
                
                window = w;
                
                a.onNext(w);
                
                WindowBoundaryInnerSubscriber<T, B> inner = new WindowBoundaryInnerSubscriber<T, B>(this);
                
                if (boundary.compareAndSet(null, inner)) {
                    windows.getAndIncrement();
                    other.subscribe(inner);
                }
            }
        }
        
        @Override
        public void onNext(T t) {
            if (fastEnter()) {
                UnicastSubject<T> w = window;
                
                w.onNext(t);
                
                if (leave(-1) == 0) {
                    return;
                }
            } else {
                queue.offer(NotificationLite.next(t));
                if (!enter()) {
                    return;
                }
            }
            drainLoop();
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(error);
                return;
            }
            error = t;
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            if (windows.decrementAndGet() == 0) {
                DisposableHelper.dispose(boundary);
            }
            
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            if (enter()) {
                drainLoop();
            }
            
            if (windows.decrementAndGet() == 0) {
                DisposableHelper.dispose(boundary);
            }

            actual.onComplete();
            
        }
        
        @Override
        public void dispose() {
            cancelled = true;
        }

        @Override
        public boolean isDisposed() {
            return cancelled;
        }

        void drainLoop() {
            final Queue<Object> q = queue;
            final Observer<? super Observable<T>> a = actual;
            int missed = 1;
            UnicastSubject<T> w = window;
            for (;;) {
                
                for (;;) {
                    boolean d = done;
                    
                    Object o = q.poll();
                    
                    boolean empty = o == null;
                    
                    if (d && empty) {
                        DisposableHelper.dispose(boundary);
                        Throwable e = error;
                        if (e != null) {
                            w.onError(e);
                        } else {
                            w.onComplete();
                        }
                        return;
                    }
                    
                    if (empty) {
                        break;
                    }
                    
                    if (o == NEXT) {
                        w.onComplete();

                        if (windows.decrementAndGet() == 0) {
                            DisposableHelper.dispose(boundary);
                            return;
                        }

                        if (cancelled) {
                            continue;
                        }
                        
                        w = UnicastSubject.create(bufferSize);
                        
                        windows.getAndIncrement();

                        window = w;
                        
                        a.onNext(w);
                        
                        continue;
                    }
                    
                    w.onNext(NotificationLite.<T>getValue(o));
                }
                
                missed = leave(-missed);
                if (missed == 0) {
                    return;
                }
            }
        }
        
        void next() {
            queue.offer(NEXT);
            if (enter()) {
                drainLoop();
            }
        }
        
        @Override
        public void accept(Observer<? super Observable<T>> a, Object v) {
            // not used by this operator
        }
    }
    
    static final class WindowBoundaryInnerSubscriber<T, B> extends DisposableObserver<B> {
        final WindowBoundaryMainSubscriber<T, B> parent;
        
        boolean done;
        
        public WindowBoundaryInnerSubscriber(WindowBoundaryMainSubscriber<T, B> parent) {
            this.parent = parent;
        }
        
        @Override
        public void onNext(B t) {
            if (done) {
                return;
            }
            parent.next();
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            done = true;
            parent.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            parent.onComplete();
        }
    }
}
