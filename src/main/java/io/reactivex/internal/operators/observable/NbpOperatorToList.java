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

import java.util.*;

import io.reactivex.Observable.NbpOperator;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Supplier;
import io.reactivex.internal.disposables.*;
import io.reactivex.internal.subscribers.observable.NbpEmptySubscriber;

public final class NbpOperatorToList<T, U extends Collection<? super T>> implements NbpOperator<U, T> {
    
    final Supplier<U> collectionSupplier;
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final NbpOperatorToList DEFAULT = new NbpOperatorToList(new Supplier() {
        @Override
        public Object get() {
            return new ArrayList();
        }
    });
    
    @SuppressWarnings("unchecked")
    public static <T> NbpOperatorToList<T, List<T>> defaultInstance() {
        return DEFAULT;
    }
    
    public NbpOperatorToList(Supplier<U> collectionSupplier) {
        this.collectionSupplier = collectionSupplier;
    }
    
    @Override
    public Observer<? super T> apply(Observer<? super U> t) {
        U coll;
        try {
            coll = collectionSupplier.get();
        } catch (Throwable e) {
            EmptyDisposable.error(e, t);
            return NbpEmptySubscriber.DISPOSED;
        }
        return new ToListSubscriber<T, U>(t, coll);
    }
    
    static final class ToListSubscriber<T, U extends Collection<? super T>> implements Observer<T> {
        U collection;
        final Observer<? super U> actual;
        
        Disposable s;
        
        public ToListSubscriber(Observer<? super U> actual, U collection) {
            this.actual = actual;
            this.collection = collection;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (DisposableHelper.validate(this.s, s)) {
                this.s = s;
                actual.onSubscribe(s);
            }
        }
        
        @Override
        public void onNext(T t) {
            collection.add(t);
        }
        
        @Override
        public void onError(Throwable t) {
            collection = null;
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            U c = collection;
            collection = null;
            actual.onNext(c);
            actual.onComplete();
        }
    }
}
