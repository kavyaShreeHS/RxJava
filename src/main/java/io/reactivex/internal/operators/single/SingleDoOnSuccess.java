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
import io.reactivex.functions.Consumer;

public class SingleDoOnSuccess<T> extends Single<T> {

    final SingleConsumable<T> source;
    
    final Consumer<? super T> onSuccess;
    
    public SingleDoOnSuccess(SingleConsumable<T> source, Consumer<? super T> onSuccess) {
        this.source = source;
        this.onSuccess = onSuccess;
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
                try {
                    onSuccess.accept(value);
                } catch (Throwable ex) {
                    s.onError(ex);
                    return;
                }
                s.onSuccess(value);
            }

            @Override
            public void onError(Throwable e) {
                s.onError(e);
            }
            
        });
    }

}
