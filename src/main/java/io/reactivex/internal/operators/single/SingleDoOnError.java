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

package io.reactivex.internal.operators.single;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.CompositeException;
import io.reactivex.functions.Consumer;

public final class SingleDoOnError<T> extends Single<T> {

    final SingleConsumable<T> source;
    
    final Consumer<? super Throwable> onError;
    
    public SingleDoOnError(SingleConsumable<T> source, Consumer<? super Throwable> onError) {
        this.source = source;
        this.onError = onError;
    }

    @Override
    protected void subscribeActual(final SingleSubscriber<? super T> s) {

        source.subscribe(new SingleSubscriber<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                s.onSubscribe(d);
            }

            @Override
            public void onSuccess(T value) {
                s.onSuccess(value);
            }

            @Override
            public void onError(Throwable e) {
                try {
                    onError.accept(e);
                } catch (Throwable ex) {
                    e = new CompositeException(ex, e);
                }
                s.onError(e);
            }
            
        });
    }

}
