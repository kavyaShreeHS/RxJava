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
import io.reactivex.plugins.RxJavaPlugins;

public final class SingleLift<T, R> extends Single<R> {

    final SingleConsumable<T> source;
    
    final SingleOperator<? extends R, ? super T> onLift;
    
    public SingleLift(SingleConsumable<T> source, SingleOperator<? extends R, ? super T> onLift) {
        this.source = source;
        this.onLift = onLift;
    }

    @Override
    protected void subscribeActual(SingleSubscriber<? super R> s) {
        try {
            SingleSubscriber<? super T> sr = onLift.apply(s);
            
            if (sr == null) {
                throw new NullPointerException("The onLift returned a null subscriber");
            }
            // TODO plugin wrapper
            source.subscribe(sr);
        } catch (NullPointerException ex) { // NOPMD
            throw ex;
        } catch (Throwable ex) {
            RxJavaPlugins.onError(ex);
            NullPointerException npe = new NullPointerException("Not really but can't throw other than NPE");
            npe.initCause(ex);
            throw npe;
        }
    }

}
