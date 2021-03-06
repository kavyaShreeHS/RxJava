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

import java.util.Iterator;

import io.reactivex.*;
import io.reactivex.disposables.*;
import io.reactivex.internal.disposables.EmptyDisposable;

public final class ObservableFromIterable<T> extends Observable<T> {
    final Iterable<? extends T> source;
    public ObservableFromIterable(Iterable<? extends T> source) {
        this.source = source;
    }
    
    @Override
    public void subscribeActual(Observer<? super T> s) {
        Iterator<? extends T> it;
        try {
            it = source.iterator();
        } catch (Throwable e) {
            EmptyDisposable.error(e, s);
            return;
        }
        boolean hasNext;
        try {
            hasNext = it.hasNext();
        } catch (Throwable e) {
            EmptyDisposable.error(e, s);
            return;
        }
        if (!hasNext) {
            EmptyDisposable.complete(s);
            return;
        }
        Disposable d = Disposables.empty();
        s.onSubscribe(d);
        
        do {
            if (d.isDisposed()) {
                return;
            }
            T v;
            
            try {
                v = it.next();
            } catch (Throwable e) {
                s.onError(e);
                return;
            }
            
            if (v == null) {
                s.onError(new NullPointerException("The iterator returned a null value"));
                return;
            }
            
            s.onNext(v);
            
            if (d.isDisposed()) {
                return;
            }
            try {
                hasNext = it.hasNext();
            } catch (Throwable e) {
                s.onError(e);
                return;
            }
        } while (hasNext);
        
        if (!d.isDisposed()) {
            s.onComplete();
        }
    }
}
