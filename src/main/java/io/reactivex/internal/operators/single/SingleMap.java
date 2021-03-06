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
import io.reactivex.functions.Function;

public final class SingleMap<T, R> extends Single<R> {
    final SingleConsumable<? extends T> source;
    
    final Function<? super T, ? extends R> mapper;

    public SingleMap(SingleConsumable<? extends T> source, Function<? super T, ? extends R> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(final SingleSubscriber<? super R> t) {
        source.subscribe(new SingleSubscriber<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                t.onSubscribe(d);
            }

            @Override
            public void onSuccess(T value) {
                R v;
                try {
                    v = mapper.apply(value);
                } catch (Throwable e) {
                    onError(e);
                    return;
                }
                
                t.onSuccess(v);
            }

            @Override
            public void onError(Throwable e) {
                t.onError(e);
            }
        });
    }
}
