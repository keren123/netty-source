/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.group;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.util.Objects.requireNonNull;

/**
 */
final class CombinedIterator<E> implements Iterator<E> {

    private final Iterator<E> i1;
    private final Iterator<E> i2;
    private Iterator<E> currentIterator;

    CombinedIterator(Iterator<E> i1, Iterator<E> i2) {
        requireNonNull(i1, "i1");
        requireNonNull(i2, "i2");
        this.i1 = i1;
        this.i2 = i2;
        currentIterator = i1;
    }

    @Override
    public boolean hasNext() {
        for (;;) {
            if (currentIterator.hasNext()) {
                return true;
            }

            if (currentIterator == i1) {
                currentIterator = i2;
            } else {
                return false;
            }
        }
    }

    @Override
    public E next() {
        for (;;) {
            try {
                return currentIterator.next();
            } catch (NoSuchElementException e) {
                if (currentIterator == i1) {
                    currentIterator = i2;
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public void remove() {
        currentIterator.remove();
    }

}
