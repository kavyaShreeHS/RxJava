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

package io.reactivex.internal.operators.completable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.*;
import io.reactivex.disposables.*;
import io.reactivex.internal.queue.MpscLinkedQueue;

public final class CompletableMergeDelayErrorIterable extends Completable {
    final Iterable<? extends CompletableConsumable> sources;
    
    public CompletableMergeDelayErrorIterable(Iterable<? extends CompletableConsumable> sources) {
        this.sources = sources;
    }
    
    @Override
    public void subscribeActual(final CompletableSubscriber s) {
        final CompositeDisposable set = new CompositeDisposable();
        
        s.onSubscribe(set);
        
        Iterator<? extends CompletableConsumable> iterator;
        
        try {
            iterator = sources.iterator();
        } catch (Throwable e) {
            s.onError(e);
            return;
        }
        
        if (iterator == null) {
            s.onError(new NullPointerException("The source iterator returned is null"));
            return;
        }

        final AtomicInteger wip = new AtomicInteger(1);
        
        final Queue<Throwable> queue = new MpscLinkedQueue<Throwable>();

        for (;;) {
            if (set.isDisposed()) {
                return;
            }
            
            boolean b;
            try {
                b = iterator.hasNext();
            } catch (Throwable e) {
                queue.offer(e);
                if (wip.decrementAndGet() == 0) {
                    if (queue.isEmpty()) {
                        s.onComplete();
                    } else {
                        s.onError(CompletableMerge.collectErrors(queue));
                    }
                }
                return;
            }
                    
            if (!b) {
                break;
            }
            
            if (set.isDisposed()) {
                return;
            }
            
            CompletableConsumable c;
            
            try {
                c = iterator.next();
            } catch (Throwable e) {
                queue.offer(e);
                if (wip.decrementAndGet() == 0) {
                    if (queue.isEmpty()) {
                        s.onComplete();
                    } else {
                        s.onError(CompletableMerge.collectErrors(queue));
                    }
                }
                return;
            }
            
            if (set.isDisposed()) {
                return;
            }
            
            if (c == null) {
                NullPointerException e = new NullPointerException("A completable source is null");
                queue.offer(e);
                if (wip.decrementAndGet() == 0) {
                    if (queue.isEmpty()) {
                        s.onComplete();
                    } else {
                        s.onError(CompletableMerge.collectErrors(queue));
                    }
                }
                return;
            }
            
            wip.getAndIncrement();
            
            c.subscribe(new CompletableSubscriber() {
                @Override
                public void onSubscribe(Disposable d) {
                    set.add(d);
                }

                @Override
                public void onError(Throwable e) {
                    queue.offer(e);
                    tryTerminate();
                }

                @Override
                public void onComplete() {
                    tryTerminate();
                }
                
                void tryTerminate() {
                    if (wip.decrementAndGet() == 0) {
                        if (queue.isEmpty()) {
                            s.onComplete();
                        } else {
                            s.onError(CompletableMerge.collectErrors(queue));
                        }
                    }
                }
            });
        }
        
        if (wip.decrementAndGet() == 0) {
            if (queue.isEmpty()) {
                s.onComplete();
            } else {
                s.onError(CompletableMerge.collectErrors(queue));
            }
        }
    }
}