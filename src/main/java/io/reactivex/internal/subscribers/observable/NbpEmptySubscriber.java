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

package io.reactivex.internal.subscribers.observable;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * A subscriber that ignores all events (onError is forwarded to RxJavaPlugins though).
 */
public enum NbpEmptySubscriber implements Observer<Object> {
    /** Empty instance that reports error to the plugins. */
    INSTANCE(false),
    /** Empty instance that disposes disposables. */
    DISPOSED(true);
    
    private final boolean disposeDisposable;
    
    NbpEmptySubscriber(boolean disposeDisposable) {
        this.disposeDisposable = disposeDisposable;
    }
    
    @Override
    public void onSubscribe(Disposable d) {
        if (disposeDisposable) {
            d.dispose();
        }
    }
    
    @Override
    public void onNext(Object t) {
        
    }
    
    @Override
    public void onError(Throwable t) {
        RxJavaPlugins.onError(t);
    }
    
    @Override
    public void onComplete() {
        
    }
}
