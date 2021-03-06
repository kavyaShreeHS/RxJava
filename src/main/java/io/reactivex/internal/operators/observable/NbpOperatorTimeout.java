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

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.*;
import io.reactivex.Observable.NbpOperator;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.*;
import io.reactivex.internal.disposables.*;
import io.reactivex.internal.subscribers.observable.*;
import io.reactivex.observers.SerializedObserver;
import io.reactivex.plugins.RxJavaPlugins;

public final class NbpOperatorTimeout<T, U, V> implements NbpOperator<T, T> {
    final Supplier<? extends ObservableConsumable<U>> firstTimeoutSelector; 
    final Function<? super T, ? extends ObservableConsumable<V>> timeoutSelector; 
    final ObservableConsumable<? extends T> other;

    public NbpOperatorTimeout(Supplier<? extends ObservableConsumable<U>> firstTimeoutSelector,
            Function<? super T, ? extends ObservableConsumable<V>> timeoutSelector, ObservableConsumable<? extends T> other) {
        this.firstTimeoutSelector = firstTimeoutSelector;
        this.timeoutSelector = timeoutSelector;
        this.other = other;
    }

    @Override
    public Observer<? super T> apply(Observer<? super T> t) {
        if (other == null) {
            return new TimeoutSubscriber<T, U, V>(
                    new SerializedObserver<T>(t), 
                    firstTimeoutSelector, timeoutSelector);
        }
        return new TimeoutOtherSubscriber<T, U, V>(t, firstTimeoutSelector, timeoutSelector, other);
    }
    
    static final class TimeoutSubscriber<T, U, V>
    extends AtomicReference<Disposable>
    implements Observer<T>, Disposable, OnTimeout {
        /** */
        private static final long serialVersionUID = 2672739326310051084L;
        final Observer<? super T> actual;
        final Supplier<? extends ObservableConsumable<U>> firstTimeoutSelector; 
        final Function<? super T, ? extends ObservableConsumable<V>> timeoutSelector; 

        Disposable s;
        
        volatile long index;
        
        public TimeoutSubscriber(Observer<? super T> actual,
                Supplier<? extends ObservableConsumable<U>> firstTimeoutSelector,
                Function<? super T, ? extends ObservableConsumable<V>> timeoutSelector) {
            this.actual = actual;
            this.firstTimeoutSelector = firstTimeoutSelector;
            this.timeoutSelector = timeoutSelector;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (DisposableHelper.validate(this.s, s)) {
                this.s = s;
                
                Observer<? super T> a = actual;
                
                ObservableConsumable<U> p;
                
                if (firstTimeoutSelector != null) {
                    try {
                        p = firstTimeoutSelector.get();
                    } catch (Throwable ex) {
                        dispose();
                        EmptyDisposable.error(ex, a);
                        return;
                    }
                    
                    if (p == null) {
                        dispose();
                        EmptyDisposable.error(new NullPointerException("The first timeout NbpObservable is null"), a);
                        return;
                    }
                    
                    TimeoutInnerSubscriber<T, U, V> tis = new TimeoutInnerSubscriber<T, U, V>(this, 0);
                    
                    if (compareAndSet(null, tis)) {
                        a.onSubscribe(s);
                        p.subscribe(tis);
                    }
                } else {
                    a.onSubscribe(s);
                }
            }
        }
        
        @Override
        public void onNext(T t) {
            long idx = index + 1;
            index = idx;

            actual.onNext(t);
            
            Disposable d = get();
            if (d != null) {
                d.dispose();
            }
            
            ObservableConsumable<V> p;
            
            try {
                p = timeoutSelector.apply(t);
            } catch (Throwable e) {
                dispose();
                actual.onError(e);
                return;
            }
            
            if (p == null) {
                dispose();
                actual.onError(new NullPointerException("The NbpObservable returned is null"));
                return;
            }
            
            TimeoutInnerSubscriber<T, U, V> tis = new TimeoutInnerSubscriber<T, U, V>(this, idx);
            
            if (compareAndSet(d, tis)) {
                p.subscribe(tis);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            dispose();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            dispose();
            actual.onComplete();
        }
        
        @Override
        public void dispose() {
            if (DisposableHelper.dispose(this)) {
                s.dispose();
            }
        }

        @Override
        public boolean isDisposed() {
            return s.isDisposed();
        }

        @Override
        public void timeout(long idx) {
            if (idx == index) {
                dispose();
                actual.onError(new TimeoutException());
            }
        }
    }
    
    interface OnTimeout {
        void timeout(long index);
        
        void onError(Throwable e);
    }
    
    static final class TimeoutInnerSubscriber<T, U, V> extends DisposableObserver<Object> {
        final OnTimeout parent;
        final long index;
        
        boolean done;
        
        public TimeoutInnerSubscriber(OnTimeout parent, final long index) {
            this.parent = parent;
            this.index = index;
        }
        
        @Override
        public void onNext(Object t) {
            if (done) {
                return;
            }
            done = true;
            dispose();
            parent.timeout(index);
        }
        
        @Override
        public void onError(Throwable t) {
            parent.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            parent.timeout(index);
        }
    }
    
    static final class TimeoutOtherSubscriber<T, U, V>
    extends AtomicReference<Disposable>
    implements Observer<T>, Disposable, OnTimeout {
        /** */
        private static final long serialVersionUID = -1957813281749686898L;
        final Observer<? super T> actual;
        final Supplier<? extends ObservableConsumable<U>> firstTimeoutSelector; 
        final Function<? super T, ? extends ObservableConsumable<V>> timeoutSelector;
        final ObservableConsumable<? extends T> other;
        final NbpFullArbiter<T> arbiter;
        
        Disposable s;
        
        boolean done;
        
        volatile long index;
        
        public TimeoutOtherSubscriber(Observer<? super T> actual,
                Supplier<? extends ObservableConsumable<U>> firstTimeoutSelector,
                Function<? super T, ? extends ObservableConsumable<V>> timeoutSelector, ObservableConsumable<? extends T> other) {
            this.actual = actual;
            this.firstTimeoutSelector = firstTimeoutSelector;
            this.timeoutSelector = timeoutSelector;
            this.other = other;
            this.arbiter = new NbpFullArbiter<T>(actual, this, 8);
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (DisposableHelper.validate(this.s, s)) {
                this.s = s;
                
                if (!arbiter.setSubscription(s)) {
                    return;
                }
                Observer<? super T> a = actual;
                
                if (firstTimeoutSelector != null) {
                    ObservableConsumable<U> p;
                    
                    try {
                        p = firstTimeoutSelector.get();
                    } catch (Throwable ex) {
                        dispose();
                        EmptyDisposable.error(ex, a);
                        return;
                    }
                    
                    if (p == null) {
                        dispose();
                        EmptyDisposable.error(new NullPointerException("The first timeout NbpObservable is null"), a);
                        return;
                    }
                    
                    TimeoutInnerSubscriber<T, U, V> tis = new TimeoutInnerSubscriber<T, U, V>(this, 0);
                    
                    if (compareAndSet(null, tis)) {
                        a.onSubscribe(arbiter);
                        p.subscribe(tis);
                    }
                } else {
                    a.onSubscribe(arbiter);
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

            if (!arbiter.onNext(t, s)) {
                return;
            }
            
            Disposable d = get();
            if (d != null) {
                d.dispose();
            }
            
            ObservableConsumable<V> p;
            
            try {
                p = timeoutSelector.apply(t);
            } catch (Throwable e) {
                actual.onError(e);
                return;
            }
            
            if (p == null) {
                actual.onError(new NullPointerException("The NbpObservable returned is null"));
                return;
            }
            
            TimeoutInnerSubscriber<T, U, V> tis = new TimeoutInnerSubscriber<T, U, V>(this, idx);
            
            if (compareAndSet(d, tis)) {
                p.subscribe(tis);
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
            arbiter.onError(t, s);
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            dispose();
            arbiter.onComplete(s);
        }
        
        @Override
        public void dispose() {
            if (DisposableHelper.dispose(this)) {
                s.dispose();
            }
        }

        @Override
        public boolean isDisposed() {
            return s.isDisposed();
        }

        @Override
        public void timeout(long idx) {
            if (idx == index) {
                dispose();
                other.subscribe(new NbpFullArbiterSubscriber<T>(arbiter));
            }
        }
    }
}
