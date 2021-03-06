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

import io.reactivex.Observable.NbpOperator;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.disposables.DisposableHelper;

public final class NbpOperatorTakeUntilPredicate<T> implements NbpOperator<T, T> {
    final Predicate<? super T> predicate;
    public NbpOperatorTakeUntilPredicate(Predicate<? super T> predicate) {
        this.predicate = predicate;
    }
    
    @Override
    public Observer<? super T> apply(Observer<? super T> s) {
        return new InnerSubscriber<T>(s, predicate);
    }
    
    static final class InnerSubscriber<T> implements Observer<T> {
        final Observer<? super T> actual;
        final Predicate<? super T> predicate;
        Disposable s;
        boolean done;
        public InnerSubscriber(Observer<? super T> actual, Predicate<? super T> predicate) {
            this.actual = actual;
            this.predicate = predicate;
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
            if (!done) {
                actual.onNext(t);
                boolean b;
                try {
                    b = predicate.test(t);
                } catch (Throwable e) {
                    done = true;
                    s.dispose();
                    actual.onError(e);
                    return;
                }
                if (b) {
                    done = true;
                    s.dispose();
                    actual.onComplete();
                }
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (!done) {
                done = true;
                actual.onError(t);
            }
        }
        
        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                actual.onComplete();
            }
        }
    }
}
