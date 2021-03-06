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
import io.reactivex.functions.Function;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.subscribers.flowable.*;

public final class FlowableMap<T, U> extends Flowable<U> {
    final Publisher<T> source;
    final Function<? super T, ? extends U> mapper;
    public FlowableMap(Publisher<T> source, Function<? super T, ? extends U> mapper) {
        this.source = source;
        this.mapper = mapper;
    }
    
    @Override
    protected void subscribeActual(Subscriber<? super U> s) {
        if (s instanceof ConditionalSubscriber) {
            source.subscribe(new MapConditionalSubscriber<T, U>((ConditionalSubscriber<? super U>)s, mapper));
        } else {
            source.subscribe(new MapSubscriber<T, U>(s, mapper));
        }
    }

    static final class MapSubscriber<T, U> extends BasicFuseableSubscriber<T, U> {
        final Function<? super T, ? extends U> mapper;

        public MapSubscriber(Subscriber<? super U> actual, Function<? super T, ? extends U> mapper) {
            super(actual);
            this.mapper = mapper;
        }

        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            
            if (sourceMode == ASYNC) {
                actual.onNext(null);
                return;
            }
            
            U v;
            
            try {
                v = nullCheck(mapper.apply(t), "The mapper function returned a null value.");
            } catch (Throwable ex) {
                fail(ex);
                return;
            }
            actual.onNext(v);
        }

        @Override
        public int requestFusion(int mode) {
            return transitiveBoundaryFusion(mode);
        }

        @Override
        public U poll() {
            T t = qs.poll();
            return t != null ? nullCheck(mapper.apply(t), "The mapper function returned a null value.") : null;
        }
    }

    static final class MapConditionalSubscriber<T, U> extends BasicFuseableConditionalSubscriber<T, U> {
        final Function<? super T, ? extends U> mapper;

        public MapConditionalSubscriber(ConditionalSubscriber<? super U> actual, Function<? super T, ? extends U> function) {
            super(actual);
            this.mapper = function;
        }

        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            
            if (sourceMode == ASYNC) {
                actual.onNext(null);
                return;
            }
            
            U v;
            
            try {
                v = nullCheck(mapper.apply(t), "The mapper function returned a null value.");
            } catch (Throwable ex) {
                fail(ex);
                return;
            }
            actual.onNext(v);
        }
        
        @Override
        public boolean tryOnNext(T t) {
            if (done) {
                return false;
            }
            
            if (sourceMode == ASYNC) {
                actual.onNext(null);
                return true;
            }
            
            U v;
            
            try {
                v = mapper.apply(t);
            } catch (Throwable ex) {
                fail(ex);
                return true;
            }
            return actual.tryOnNext(v);
        }

        @Override
        public int requestFusion(int mode) {
            return transitiveBoundaryFusion(mode);
        }

        @Override
        public U poll() {
            T t = qs.poll();
            return t != null ? nullCheck(mapper.apply(t), "The mapper function returned a null value.") : null;
        }
    }

}
