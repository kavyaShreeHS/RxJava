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

import org.reactivestreams.*;

import io.reactivex.Flowable;

public final class FlowableWrapper<T> extends Flowable<T> {
    final Publisher<? extends T> publisher;

    public FlowableWrapper(Publisher<? extends T> publisher) {
        this.publisher = publisher;
    }
    
    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        publisher.subscribe(s);
    }
}
